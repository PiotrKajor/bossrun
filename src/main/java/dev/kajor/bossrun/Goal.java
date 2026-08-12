package dev.kajor.bossrun;

import java.util.List;

/**
 * Jeden cel wyzwania. Czterech typow starcza na wszystko, co da sie sprawdzic tanio
 * i bez zgadywania - a ADVANCEMENT jest furtka na reszte: kazdy warunek, ktorego tu nie ma,
 * wlasciciel serwera wyraza wlasnym osiagnieciem w datapacku i podaje jego id.
 *
 * Tozsamoscia celu jest {@code target}. Dzieki temu stan zapisany starsza wersja moda
 * (lista zabitych bossow po id encji) wczytuje sie bez zadnej migracji.
 */
public record Goal(Type type, String target, int amount, String name) {

    public enum Type {
        /** Zabic {@code amount} sztuk encji o tym id. */
        KILL,
        /** Miec {@code amount} sztuk przedmiotu w ekwipunku. */
        HAVE,
        /** Wejsc do wymiaru o tym id. */
        REACH,
        /** Odblokowac osiagniecie o tym id - wanilkowe albo z datapacka. */
        ADVANCEMENT
    }

    /** Domyslne wyzwanie: cztery bossy, po jednym. */
    public static List<Goal> defaults() {
        return List.of(
                new Goal(Type.KILL, "minecraft:ender_dragon", 1, "Ender Dragon"),
                new Goal(Type.KILL, "minecraft:wither", 1, "Wither"),
                new Goal(Type.KILL, "minecraft:elder_guardian", 1, "Elder Guardian"),
                new Goal(Type.KILL, "minecraft:warden", 1, "Warden"));
    }

    /** Nazwa na TAB-ie. Mod jest server-side, wiec to zwykly tekst z configu, nie klucz tlumaczenia. */
    public String label() {
        if (name != null && !name.isBlank()) return name;
        String bare = target.contains(":") ? target.substring(target.indexOf(':') + 1) : target;
        String pretty = bare.replace('_', ' ');
        String head = pretty.isEmpty() ? pretty : Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
        return amount > 1 ? head + " x" + amount : head;
    }

    /** Puste pola w JSON-ie nie moga wywrocic serwera - brakujacy typ to KILL, brakujaca liczba to 1. */
    public Goal repaired() {
        return new Goal(type == null ? Type.KILL : type,
                target == null ? "minecraft:ender_dragon" : target,
                amount < 1 ? 1 : amount,
                name);
    }
}
