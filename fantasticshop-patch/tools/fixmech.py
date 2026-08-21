#!/usr/bin/env python3
"""
Repairs the mechanical artefacts CFR leaves behind, so the decompiled FantasticShop sources
compile again. None of these change behaviour.

  1. Redundant (Object) casts CFR inserts on generic call sites.
  2. (GuiEventListener) casts on addRenderableWidget, which defeat its type inference
     (<T extends GuiEventListener & Renderable & NarratableEntry>).
  3. ArrayList<MutableComponent> / ArrayList<Object> locals passed where List<Component>
     is expected.
  4. CreativeModeTabs constants, which Forge's access transformers make public at runtime but
     are private in the plain SRG jar. Rebuilt as ResourceKey.create(...) calls, which need no
     special access. SRG names resolved from ForgeGradle's srg_to_official mapping:
       Registries.CREATIVE_MODE_TAB              -> f_279569_
       ResourceKey.create(ResourceKey, ResLoc)   -> m_135785_
"""
import re
import sys

# SRG field -> vanilla creative tab id, verified by pairing all 15 CreativeModeTabs fields
# of the SRG jar against the official-named jar position by position.
CREATIVE_TABS = {
    "f_256788_": "building_blocks",
    "f_256776_": "natural_blocks",
    "f_256725_": "colored_blocks",
    "f_256791_": "functional_blocks",
    "f_256797_": "combat",
    "f_256869_": "tools_and_utilities",
    "f_256839_": "food_and_drinks",
    "f_257028_": "redstone_blocks",
    "f_256968_": "ingredients",
    "f_256731_": "spawn_eggs",
}


def fix(text):
    changes = 0

    # 1 + 2: drop the casts CFR added.
    for cast in ("(Object)", "(GuiEventListener)"):
        before = text
        text = text.replace(cast, "")
        changes += before != text

    # 3: widen tooltip list locals to List<Component>.
    def widen(match):
        return f"{match.group(1)}List<Component> {match.group(2)} = new ArrayList<Component>("
    before = text
    text = re.sub(r"(\s+)ArrayList<(?:MutableComponent|Object|Component)> (\w+) = new ArrayList\(",
                  widen, text)
    text = re.sub(r"(\s+)ArrayList<(?:MutableComponent|Object|Component)> (\w+) = new ArrayList<[^>]*>\(",
                  widen, text)
    changes += before != text

    # 4: rebuild the creative tab keys.
    for srg, tab_id in CREATIVE_TABS.items():
        needle = f"CreativeModeTabs.{srg}"
        if needle in text:
            replacement = (f'ResourceKey.m_135785_(Registries.f_279569_, '
                           f'new ResourceLocation("{tab_id}"))')
            text = text.replace(needle, replacement)
            changes += 1

    # make sure the imports those rewrites need are present
    if "ResourceKey.m_135785_(Registries.f_279569_" in text:
        for imp in ("net.minecraft.core.registries.Registries",
                    "net.minecraft.resources.ResourceKey",
                    "net.minecraft.resources.ResourceLocation"):
            if f"import {imp};" not in text:
                text = re.sub(r"^(package [^\n]+\n)", rf"\1\nimport {imp};\n", text, count=1, flags=re.M)

    if "List<Component>" in text and "import java.util.List;" not in text:
        text = re.sub(r"^(package [^\n]+\n)", r"\1\nimport java.util.List;\n", text, count=1, flags=re.M)
    if "new ArrayList<Component>" in text and "import java.util.ArrayList;" not in text:
        text = re.sub(r"^(package [^\n]+\n)", r"\1\nimport java.util.ArrayList;\n", text, count=1, flags=re.M)

    return text, changes


if __name__ == "__main__":
    for path in sys.argv[1:]:
        with open(path, encoding="utf-8") as handle:
            original = handle.read()
        patched, count = fix(original)
        if patched != original:
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(patched)
            print(f"  fixed {path} ({count} pattern groups)")
