package io.github.itzispyder.impropers3dminimap.mixin;

import io.github.itzispyder.impropers3dminimap.mixininterface.FontManagerAccessor;
import net.minecraft.client.font.EffectGlyph;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.GlyphProvider;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(FontManager.class)
public abstract class MixinFontManager implements FontManagerAccessor {

    @Shadow @Final private Map<Identifier, FontStorage> fontStorages;
    @Shadow @Final private FontStorage missingStorage;

    // TextRenderer's constructor no longer takes (Function<Identifier, FontStorage>, boolean).
    // Checked TextRenderer.java: it now takes a single TextRenderer.GlyphsProvider, a nested
    // interface with getGlyphs(StyleSpriteSource) and getRectangleGlyph(). Modeled this
    // implementation directly on vanilla FontManager's own private `Fonts` inner class (which
    // implements GlyphsProvider the same way), except this always resolves to the single fixed
    // fontId's FontStorage rather than switching on the FontManager's own font sets - matching
    // the original mixin's intent of ignoring the requested glyph source.
    @Override
    public TextRenderer createRenderer(Identifier fontId) {
        // IMPORTANT: do not resolve the FontStorage once here and capture it in the closure.
        // This method is invoked from MixinMinecraftClient#initFont, which hooks
        // onFontOptionsChanged() - and that fires once during early client startup, BEFORE the
        // resource manager has completed its first reload. At that point fontStorages doesn't
        // contain our custom font yet, so a one-time lookup permanently binds this renderer to
        // `missingStorage` (an empty/blank font), and every glyph renders as the tofu/missing-
        // glyph box forever - even after resources finish loading. Re-resolving fontStorages on
        // every call means the renderer self-heals the moment the real font finishes loading.
        return new TextRenderer(new TextRenderer.GlyphsProvider() {
            @Override
            public GlyphProvider getGlyphs(StyleSpriteSource source) {
                return currentStorage().getGlyphs(false);
            }

            @Override
            public EffectGlyph getRectangleGlyph() {
                return currentStorage().getRectangleBakedGlyph();
            }

            private FontStorage currentStorage() {
                return MixinFontManager.this.fontStorages.getOrDefault(fontId, MixinFontManager.this.missingStorage);
            }
        });
    }
}
