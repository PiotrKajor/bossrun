package pl.skynetgames.bossrun.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pl.skynetgames.bossrun.Freeze;

/**
 * Domknięcie pauzy na poziomie pakietów.
 *
 * <p>{@link Freeze} blokuje to, na co Fabric API ma zdarzenia: kopanie, użycie bloku,
 * przedmiotu i encji. Poza ich zasięgiem zostaje własny ekwipunek gracza — a tam da się
 * grać: skrzyneczkowy craft 2×2, zakładanie zbroi, przekładanie i wyrzucanie rzeczy.
 * Zamknięcie GUI nie pomaga, bo ekwipunek gracza to menu, którego zamknąć się nie da.
 *
 * <p>Dlatego pakiety odrzucamy tutaj, a klientowi odsyłamy prawdziwy stan menu — inaczej
 * widziałby u siebie ruch przedmiotu, którego serwer nie wykonał.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class PauseGuardMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void bossrun$noContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        bossrun$rejectAndResync(ci);
    }

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void bossrun$noPlaceRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
        bossrun$rejectAndResync(ci);
    }

    @Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
    private void bossrun$noContainerButton(ServerboundContainerButtonClickPacket packet, CallbackInfo ci) {
        bossrun$rejectAndResync(ci);
    }

    /**
     * Jedno wejście dla wyrzucania rzeczy, zamiany z drugą ręką i puszczenia
     * naciągniętego łuku — wszystkie chodzą tym samym pakietem.
     */
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void bossrun$noPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        bossrun$rejectAndResync(ci);
    }

    private void bossrun$rejectAndResync(CallbackInfo ci) {
        if (!Freeze.active()) return;
        ci.cancel();
        if (player != null && player.containerMenu != null) {
            player.containerMenu.sendAllDataToRemote();
        }
    }
}
