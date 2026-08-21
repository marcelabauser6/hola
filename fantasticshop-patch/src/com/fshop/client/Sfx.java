package com.fshop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * FShop interface sounds.
 *
 * <p>Patched for Fantastic Currency: every call used to funnel into a single
 * {@code AMETHYST_BLOCK_CHIME} (SRG {@code f_144243_}), which is the bright glassy ping. Replaced
 * with a softer, warmer palette built from plain {@code SoundEvent} fields, and each action now
 * gets a sound that suits it instead of all six sharing one.</p>
 *
 * <p>The public method signatures are unchanged so the rest of FShop links against this exactly
 * as before.</p>
 */
public final class Sfx {

    /** Soft muted click used across the loom UI - reads as a gentle tap. */
    private static final SoundEvent SOFT_CLICK = SoundEvents.f_12491_;   // UI_LOOM_SELECT_PATTERN
    /** Warm confirmation, no metallic ring. */
    private static final SoundEvent SOFT_CONFIRM = SoundEvents.f_12492_; // UI_LOOM_TAKE_RESULT
    /** Paper rustle for anything page-like. */
    private static final SoundEvent PAGE_TURN = SoundEvents.f_11713_;    // BOOK_PAGE_TURN
    /** Muffled cloth, the quietest of the set. */
    private static final SoundEvent MUFFLED = SoundEvents.f_12641_;      // WOOL_HIT
    /** Soft cloth shuffle. */
    private static final SoundEvent CLOTH = SoundEvents.f_184215_;       // BUNDLE_INSERT

    private Sfx() {
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc != null && mc.m_91106_() != null) {
            SimpleSoundInstance instance = SimpleSoundInstance.m_119755_(sound, pitch, volume);
            mc.m_91106_().m_120367_((SoundInstance) instance);
        }
    }

    public static void spark(float pitch) {
        play(CLOTH, 1.0f, 0.28f);
    }

    public static void click() {
        play(SOFT_CLICK, 1.0f, 0.30f);
    }

    public static void step() {
        play(MUFFLED, 1.15f, 0.22f);
    }

    public static void page() {
        play(PAGE_TURN, 0.95f, 0.30f);
    }

    public static void success() {
        play(SOFT_CONFIRM, 1.0f, 0.36f);
    }

    public static void select() {
        play(SOFT_CLICK, 1.1f, 0.26f);
    }
}
