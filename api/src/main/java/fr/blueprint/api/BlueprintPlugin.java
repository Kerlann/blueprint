package fr.blueprint.api;

/**
 * Point d'entrée d'un mod tiers dans Blueprint.
 *
 * <p>Un mod déclare son implémentation dans {@code fabric.mod.json} :
 * <pre>{@code
 * "entrypoints": { "blueprint": ["com.example.MyPlugin"] }
 * }</pre>
 *
 * <p>La surface d'enregistrement ({@code registerNodes}, {@code registerTypes},
 * {@code registerEvents}) est figée en story 2.2 — voir {@code docs/extension-api.md}.
 */
public interface BlueprintPlugin {
}
