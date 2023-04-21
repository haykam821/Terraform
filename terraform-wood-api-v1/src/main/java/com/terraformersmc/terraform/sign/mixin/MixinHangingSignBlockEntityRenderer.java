package com.terraformersmc.terraform.sign.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.entity.HangingSignBlockEntityRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.terraformersmc.terraform.sign.TerraformSign;

@Mixin(HangingSignBlockEntityRenderer.class)
@Environment(EnvType.CLIENT)
public abstract class MixinHangingSignBlockEntityRenderer extends MixinSignBlockEntityRenderer {
	@Inject(method = "getTextureId", at = @At("HEAD"), cancellable = true)
	private void getHangingSignTextureId(CallbackInfoReturnable<SpriteIdentifier> ci) {
		if (this.terraform$renderedBlockEntity != null) {
			if (this.terraform$renderedBlockEntity.getCachedState().getBlock() instanceof TerraformSign signBlock) {
				ci.setReturnValue(new SpriteIdentifier(TexturedRenderLayers.SIGNS_ATLAS_TEXTURE, signBlock.getTexture()));
			}
		}
	}
}
