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
 * Era 1.20.1.
 *
 * Wszystko, co Mojang zmienil miedzy wydaniami, siedzi w tej jednej klasie: po jednym
 * pliku na ere, w src/compat/<era>. Reszta moda jest wspolna i o zadnej roznicy nie wie.
 *
 * Tu jak w 1.20.4, poza dwiema rzeczami:
 *  - osiagniecia zwraca sie jako Advancement, nie AdvancementHolder (ten wszedl w 1.20.2),
 *  - nie ma TickRateManagera - komenda /tick i cale zamrazanie czasu weszly dopiero
 *    w 1.20.4, wiec pauze skladamy z regul gry.
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
        var advancement = server.getAdvancements().getAdvancement(id);
        if (advancement == null) return null;   // null = nieznane, wolajacy to zaloguje
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
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

    // --- Pauza bez TickRateManagera -----------------------------------------
    // 1.20.1 nie zna /tick freeze, wiec czasu nie da sie zatrzymac jednym przelacznikiem.
    // Skladamy go z regul gry: doba, pogoda, spawny, ogien, losowe ticki. Ruchu juz
    // zyjacych mobow to nie zatrzyma, ale krzywdy nie zrobia - Freeze i tak odrzuca
    // kazde obrazenie gracza podczas pauzy (ALLOW_DAMAGE), wiec czekanie jest bezpieczne.
    private static final java.util.List<net.minecraft.world.level.GameRules.Key<
            net.minecraft.world.level.GameRules.BooleanValue>> WYLACZANE = java.util.List.of(
            net.minecraft.world.level.GameRules.RULE_DAYLIGHT,
            net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE,
            net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING,
            net.minecraft.world.level.GameRules.RULE_DOFIRETICK,
            net.minecraft.world.level.GameRules.RULE_DOINSOMNIA,
            net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);

    private static final java.util.Map<net.minecraft.world.level.GameRules.Key<
            net.minecraft.world.level.GameRules.BooleanValue>, Boolean> POPRZEDNIE =
            new java.util.HashMap<>();
    private static int poprzedniRandomTick = -1;
    private static boolean zamrozone;

    static void zamrozenie(MinecraftServer server, boolean wlaczone) {
        if (wlaczone == zamrozone) return;
        zamrozone = wlaczone;
        net.minecraft.world.level.GameRules zasady = server.getGameRules();
        if (wlaczone) {
            POPRZEDNIE.clear();
            for (var klucz : WYLACZANE) {
                POPRZEDNIE.put(klucz, zasady.getBoolean(klucz));
                zasady.getRule(klucz).set(false, server);
            }
            poprzedniRandomTick = zasady.getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);
            zasady.getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(0, server);
        } else {
            for (var klucz : WYLACZANE) {
                zasady.getRule(klucz).set(POPRZEDNIE.getOrDefault(klucz, Boolean.TRUE), server);
            }
            if (poprzedniRandomTick >= 0) {
                zasady.getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING)
                        .set(poprzedniRandomTick, server);
            }
        }
    }

    /** Wlasna flaga - w 1.20.1 nie ma wanilkowego stanu, o ktory mozna by zapytac. */
    static boolean czyZamrozone(MinecraftServer server) {
        return zamrozone;
    }

    /** Cel na TAB-ie. W 1.20.1 addObjective ma cztery pola, a sloty sa liczbami:
     *  LIST to 0 (enum DisplaySlot wszedl dopiero w 1.20.3). */
    static net.minecraft.world.scores.Objective celTabu(
            net.minecraft.world.scores.Scoreboard sb, String nazwa,
            net.minecraft.network.chat.Component tytul) {
        net.minecraft.world.scores.Objective cel = sb.getObjective(nazwa);
        if (cel == null) {
            cel = sb.addObjective(nazwa,
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, tytul,
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER);
        }
        sb.setDisplayObjective(0, cel);
        return cel;
    }

    /** Wynik gracza; w 1.20.1 adresuje sie go nickiem, a setter nazywa sie setScore. */
    static void ustawWynik(net.minecraft.world.scores.Scoreboard sb, ServerPlayer gracz,
                           net.minecraft.world.scores.Objective cel, int wartosc) {
        sb.getOrCreatePlayerScore(gracz.getScoreboardName(), cel).setScore(wartosc);
    }
}
