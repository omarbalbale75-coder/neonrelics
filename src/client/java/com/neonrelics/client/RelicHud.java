package com.neonrelics.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import com.neonrelics.NeonRelics;
import com.neonrelics.Relic;
import com.neonrelics.RelicData;
import com.neonrelics.network.HudPayload;

/**
 * Draws the relic panel (left side), the Cyber neural-load / chrome bar (right side)
 * and full-screen tints for Overdrive and Cyber Maniac.
 */
public final class RelicHud {
	private RelicHud() { }

	private static final int ICON_TEXTURE_SIZE = 32;

	@Nullable
	private static HudPayload state;
	private static long receivedAt;

	public static void update(HudPayload payload) {
		state = payload;
		receivedAt = Util.getMillis();
	}

	public static void clear() {
		state = null;
	}

	/** Server sends updates every 1/4 second - count down smoothly in between. */
	private static int live(int ticks) {
		long elapsed = (Util.getMillis() - receivedAt) / 50L;
		return (int) Math.max(0L, ticks - elapsed);
	}

	private static String time(int ticks) {
		int seconds = (ticks + 19) / 20;
		return seconds >= 60 ? String.format("%d:%02d", seconds / 60, seconds % 60) : seconds + "s";
	}

	private static int argb(int alpha, int rgb) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	private static float pulse(double speed) {
		return (float) (0.5 + 0.5 * Math.sin(Util.getMillis() / 1000.0 * speed));
	}

	// ------------------------------------------------------------------
	// Relic panel
	// ------------------------------------------------------------------

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		HudPayload s = state;

		if (s == null || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}

		Relic relic = Relic.byId(s.relic());

		if (relic == null) {
			return;
		}

		Font font = mc.font;
		String key = Component.keybind("key.neonrelics.ability").getString();

		String title = relic.displayName + (s.mastered() ? " ★" : "");
		String line1;
		int line1Color;
		String line2 = null;
		int line2Color = 0xFFAAAAAA;

		int active = live(s.active());
		int cooldown = live(s.cooldown());

		if (!relic.hasAbility()) {
			line1 = "Passive relic";
			line1Color = 0xFFAAAAAA;
		} else if (relic == Relic.CYBER && s.implant().isEmpty()) {
			line1 = "No implant [" + key + "]";
			line1Color = 0xFF55FFFF;
		} else if (relic == Relic.CYBER && s.implant().equals(RelicData.IMPLANT_CHROME)) {
			line1 = "Chrome Body";
			line1Color = 0xFFFF55FF;
		} else if (active > 0) {
			line1 = (relic == Relic.CYBER ? "OVERDRIVE " : "ACTIVE ") + time(active);
			line1Color = argb(255, relic.color);
		} else if (cooldown > 0) {
			line1 = relic.abilityName + ": " + time(cooldown);
			line1Color = 0xFF888888;
		} else {
			line1 = relic.abilityName + " [" + key + "]";
			line1Color = 0xFF55FF55;
		}

		if (relic.masteryGoal > 0) {
			line2 = s.mastered() ? "Mastered" : "Mastery " + s.mastery() + "/" + s.masteryGoal();
			line2Color = s.mastered() ? 0xFFFFAA00 : 0xFFAAAAAA;
		} else if (relic == Relic.CYBER && live(s.passiveCooldown()) > 0) {
			line2 = "Passive: " + time(live(s.passiveCooldown()));
		} else if (relic == Relic.CYBER && s.implant().equals(RelicData.IMPLANT_BOOT)) {
			line2 = "Boot Accelerator";
			line2Color = 0xFF55FFFF;
		}

		int textWidth = Math.max(font.width(title), Math.max(font.width(line1), line2 == null ? 0 : font.width(line2)));
		int width = 24 + 6 + textWidth + 8;
		int height = line2 == null ? 30 : 38;
		int x = 4;
		int y = graphics.guiHeight() / 2 - height / 2 - 20;

		// Panel with a neon border in the relic's colour.
		int border = argb(s.mastered() ? (int) (150 + 105 * pulse(3.0)) : 170, relic.color);
		graphics.fill(x, y, x + width, y + height, 0x99000000);
		graphics.fill(x, y, x + width, y + 1, border);
		graphics.fill(x, y + height - 1, x + width, y + height, border);
		graphics.fill(x, y, x + 1, y + height, border);
		graphics.fill(x + width - 1, y, x + width, y + height, border);

		// Icon (32x32 texture drawn at 24x24).
		Identifier icon = NeonRelics.id("textures/gui/relic/" + relic.id + ".png");
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 3, y + (height - 24) / 2.0F);
		graphics.pose().scale(0.75F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 0.0F, 0.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
		graphics.pose().popMatrix();

		int tx = x + 30;
		graphics.text(font, title, tx, y + 4, argb(255, relic.color));
		graphics.text(font, line1, tx, y + 15, line1Color);

		if (line2 != null) {
			graphics.text(font, line2, tx, y + 26, line2Color);
		}

		if (relic == Relic.CYBER) {
			extractCyberBar(graphics, font, s);
		}
	}

	/** The bar on the side of the screen NOVA keeps telling you to watch. */
	private static void extractCyberBar(GuiGraphicsExtractor graphics, Font font, HudPayload s) {
		boolean chrome = s.implant().equals(RelicData.IMPLANT_CHROME);
		boolean boot = s.implant().equals(RelicData.IMPLANT_BOOT);

		if (!chrome && !boot) {
			return;
		}

		int maniac = live(s.maniac());
		int barHeight = 90;
		int x = graphics.guiWidth() - 14;
		int y = graphics.guiHeight() / 2 - barHeight / 2;
		float fill;
		int color;
		String label;
		String value;

		if (chrome) {
			fill = s.maniacMax() > 0 ? maniac / (float) s.maniacMax() : 0.0F;
			color = fill < 0.15F ? argb((int) (150 + 105 * pulse(8.0)), 0xFF1A3C) : 0xFFFF55FF;
			label = "CHR";
			value = time(maniac);
		} else if (maniac > 0) {
			fill = s.maniacMax() > 0 ? maniac / (float) s.maniacMax() : 0.0F;
			color = argb((int) (150 + 105 * pulse(8.0)), 0xFF1A3C);
			label = "MAN";
			value = time(maniac);
		} else {
			fill = Math.min(1.0F, s.strain() / 100.0F);
			color = fill < 0.6F ? 0xFF33FFCC : fill < 0.85F ? 0xFFFFD02E : argb((int) (150 + 105 * pulse(6.0)), 0xFF2E2E);
			label = "LOAD";
			value = Math.round(s.strain()) + "%";
		}

		graphics.fill(x - 1, y - 1, x + 7, y + barHeight + 1, 0xCC000000);
		graphics.fill(x, y, x + 6, y + barHeight, 0x6611222A);
		int filled = Math.round(barHeight * Math.max(0.0F, Math.min(1.0F, fill)));
		graphics.fill(x, y + barHeight - filled, x + 6, y + barHeight, color);

		// Danger marks at 60% and 85%.
		if (boot && maniac == 0) {
			graphics.fill(x - 2, y + barHeight - Math.round(barHeight * 0.6F), x + 8, y + barHeight - Math.round(barHeight * 0.6F) + 1, 0xAAFFD02E);
			graphics.fill(x - 2, y + barHeight - Math.round(barHeight * 0.85F), x + 8, y + barHeight - Math.round(barHeight * 0.85F) + 1, 0xAAFF2E2E);
		}

		graphics.text(font, label, x + 7 - font.width(label), y - 11, 0xFF55FFFF);
		graphics.text(font, value, x + 7 - font.width(value), y + barHeight + 3, color | 0xFF000000);
	}

	// ------------------------------------------------------------------
	// Full-screen effects
	// ------------------------------------------------------------------

	public static void extractScreenEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		HudPayload s = state;

		if (s == null || mc.player == null || !Relic.CYBER.id.equals(s.relic())) {
			return;
		}

		int w = graphics.guiWidth();
		int h = graphics.guiHeight();

		// Overdrive: the world goes green-cyan with scanlines.
		if (live(s.active()) > 0) {
			graphics.fill(0, 0, w, h, 0x2200FFC0);

			for (int y = (int) (Util.getMillis() / 40L % 4L); y < h; y += 4) {
				graphics.fill(0, y, w, y + 1, 0x1400FFFF);
			}
		}

		// Cyber Maniac: a pulsing red vignette.
		if (live(s.maniac()) > 0) {
			boolean chrome = s.implant().equals(RelicData.IMPLANT_CHROME);
			int alpha = (int) ((chrome ? 25 : 45) + (chrome ? 20 : 50) * pulse(chrome ? 2.0 : 5.0));
			int edge = Math.max(8, w / 14);

			for (int i = 0; i < edge; i += 2) {
				int a = alpha * (edge - i) / edge;
				int c = argb(a, 0xFF1A3C);
				graphics.fill(i, i, w - i, i + 2, c);
				graphics.fill(i, h - i - 2, w - i, h - i, c);
				graphics.fill(i, i + 2, i + 2, h - i - 2, c);
				graphics.fill(w - i - 2, i + 2, w - i, h - i - 2, c);
			}
		}
	}
}
