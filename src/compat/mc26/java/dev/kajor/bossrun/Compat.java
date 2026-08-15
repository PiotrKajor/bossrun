package dev.kajor.bossrun;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Era 26.x - Minecraft bez obfuskacji.
 *
 * Wszystko, co Mojang zmienil miedzy wydaniami, siedzi w tej jednej klasie: po jednym
 * pliku na ere, w src/compat/<era>. Reszta moda jest wspolna i o zadnej roznicy nie wie.
 *
 * Tu: identyfikatory to Identifier (dawniej ResourceLocation), uprawnienia sa nazwane,
 * a GameProfile jest rekordem z name().
 */
final class Compat {
    private Compat() {}

    /** Odpowiednik dawnego poziomu 2. */
    static boolean operator(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static String nick(ServerPlayer player) {
        return player.getGameProfile().name();
    }

    /** Wymiar gracza jako "minecraft:the_nether" - do porownania z celem z configu. */
    static String wymiar(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    /** Przedmiot po identyfikatorze z configu; null, gdy identyfikator jest nieznany. */
    static Item przedmiot(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? null : item;
    }

    static String idBytu(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /** Czy gracz ma odblokowane osiagniecie. Null-owe id i nieznane osiagniecie = nie ma. */
    static Boolean maOsiagniecie(ServerPlayer player, String advancementId) {
        Identifier id = Identifier.tryParse(advancementId);
        if (id == null) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        var holder = server.getAdvancements().get(id);
        if (holder == null) return null;   // null = nieznane, wolajacy to zaloguje
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Katalog serwera; od 1.21.2 API zwraca Path, wczesniej File. */
    static java.nio.file.Path katalogSerwera(MinecraftServer server) {
        return server.getServerDirectory();
    }

    /** Spowolnienie na czas pauzy; od 1.21.2 efekty chodza przez Holder. */
    static void spowolnij(ServerPlayer player) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 255, false, false, false));
    }

    static void odspowolnij(ServerPlayer player) {
        player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
    }

    /** Blokada uzycia przedmiotu w czasie pauzy. Od 1.21.2 event zwraca InteractionResult. */
    static void blokujUzyciePrzedmiotu(java.util.function.BooleanSupplier pauza) {
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
                pauza.getAsBoolean() ? net.minecraft.world.InteractionResult.FAIL
                                     : net.minecraft.world.InteractionResult.PASS);
    }
}
