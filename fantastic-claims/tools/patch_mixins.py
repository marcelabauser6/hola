import io, json, sys

NUEVOS = ["HangingEntityMixin", "HopperGuardMixin", "FluidGuardMixin"]

path = sys.argv[1]
data = json.load(io.open(path, encoding="utf-8"))
mixins = data.get("mixins", [])
for m in NUEVOS:
    if m not in mixins:
        mixins.append(m)
        print("mixins.json -> anadido " + m)
data["mixins"] = mixins
io.open(path, "w", encoding="utf-8").write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
print("mixins: " + ", ".join(mixins))
