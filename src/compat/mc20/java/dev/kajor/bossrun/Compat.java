package dev.kajor.bossrun;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Era 1.20.4 - 1.21.4.
 *
 * Wszystko, co Mojang zmienil miedzy wydaniami, siedzi w tej jednej klasie: po jednym
 * pliku na ere, w src/compat/<era>. Reszta moda jest wspolna i o zadnej roznicy nie wie.
 *
 * Tu: identyfikatory to jeszcze ResourceLocation, uprawnienia sa numerowane, a GameProfile
 * jest zwykla klasa z getName().
 */
final class Compat {
    private Compat() {}

    /** Odpowiednik dawnego poziomu 2. */
    static boolean operator(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    static String nick(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /** Wymiar gracza jako "minecraft:the_nether" - do porownania z celem z configu. */
    static String wymiar(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    /** Przedmiot po identyfikatorze z configu; null, gdy identyfikator jest nieznany. */
    static Item przedmiot(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    static String idBytu(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /** Czy gracz ma odblokowane osiagniecie. Null-owe id i nieznane osiagniecie = nie ma. */
    static Boolean maOsiagniecie(ServerPlayer player, String advancementId) {
        ResourceLocation id = ResourceLocation.tryParse(advancementId);
        if (id == null) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        var holder = server.getAdvancements().get(id);
        if (holder == null) return null;   // null = nieznane, wolajacy to zaloguje
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Katalog serwera; do 1.21.1 API zwraca File, nie Path. */
    static java.nio.file.Path katalogSerwera(MinecraftServer server) {
        return server.getServerDirectory().toPath();
    }

    /** Spowolnienie na czas pauzy; do 1.21.1 efekt podaje sie wprost, bez Holdera,
     *  a nazywa sie MOVEMENT_SLOWDOWN. */
    static void spowolnij(ServerPlayer player) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false, false));
    }

    static void odspowolnij(ServerPlayer player) {
        player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
    }

    /** Blokada uzycia przedmiotu w czasie pauzy. Do 1.21.1 ten event zwraca
     *  InteractionResultHolder<ItemStack>, nie samo InteractionResult. */
    static void blokujUzyciePrzedmiotu(java.util.function.BooleanSupplier pauza) {
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            return pauza.getAsBoolean()
                    ? net.minecraft.world.InteractionResultHolder.fail(stack)
                    : net.minecraft.world.InteractionResultHolder.pass(stack);
        });
    }
}
