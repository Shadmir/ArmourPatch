# ArmourPatch

A Minecraft 1.7.10 Forge coremod that fixes armour absorption being capped by item durability.

## What it fixes

In vanilla Forge, `ISpecialArmor.ArmorProperties` calculates `absorbMax` using the item's remaining durability (`getMaxDamage() + 1 - getItemDamage()`). This causes armour pieces to absorb less damage as they wear down, even for armour types where this makes no sense (e.g. LOTR mod armour).

This mod replaces that calculation with `Integer.MAX_VALUE` so absorption is never artificially capped by durability.

## Installation

Drop the jar into your server's `mods/` folder. No configuration required. This is a server-side only mod — it does not need to be installed on the client.

## Compatibility

- Minecraft 1.7.10
- Forge 10.13.4.1614
