package de.z0rdak.yawp.wand.mixin;

import com.mojang.brigadier.CommandDispatcher;

import de.z0rdak.yawp.commands.CommandRegistry;
import de.z0rdak.yawp.wand.WandHook;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the wand to YAWP's command tree from inside YAWP's own registration.
 *
 * <p>A mixin rather than an edit to {@link CommandRegistry}: every existing class in the jar stays byte
 * for byte what it was, which is the point when the jar is a community build carrying a fix of its own.
 * The wand is new files plus one line in the manifest, and nothing else.</p>
 *
 * <p>Injected at TAIL, after YAWP has registered its {@code yawp} literal, so the node exists to attach
 * to. {@code remap = false} because the target is YAWP's own class and method - there is no obfuscated
 * name to look up, and asking for one would need a refmap this has no reason to carry.</p>
 *
 * <p>The mixin config sets {@code required} to false. A mixin that fails to apply should leave the server
 * without a wand, not without YAWP.</p>
 */
@Mixin(value = CommandRegistry.class, remap = false)
public abstract class CommandRegistryMixin {

    @Inject(method = "registerCommands", at = @At("TAIL"), remap = false)
    private static void yawpwand$addWandSubcommand(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext,
            Commands.CommandSelection selection,
            CallbackInfo callback) {
        WandHook.install(dispatcher);
    }
}
