plugins {
    id("fabric-loom")
    jacoco
}

dependencies {
    implementation(project(path = ":api", configuration = "namedElements"))
    implementation(project(path = ":core", configuration = "namedElements"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Comme core : les tests tournent sans Minecraft démarré (coding-standards §7).
    // Camera, NodeGeometry et le banc de rendu sont de la logique pure.
    testLogging {
        events("failed", "skipped")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
    }
}

// NFR13 sur le client. L'architecture de l'éditeur est « état pur testable + widget
// Minecraft mince » (coding-standards §7) : ce seuil est ce qui EMPÊCHE la logique de
// redescendre dans les widgets. La liste ci-dessous n'est donc pas une échappatoire —
// c'est l'énoncé de ce qui ne tourne pas sans jeu lancé, et elle doit rester courte.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            classDirectories.setFrom(sourceSets.main.get().output.classesDirs.asFileTree.matching {
                // Point d'entrée et réseau : exigent un client Minecraft vivant.
                exclude("fr/blueprint/client/BlueprintClient*")
                exclude("fr/blueprint/client/net/**")
                // Écrans et rendu : leur LOGIQUE est extraite dans les états et les
                // helpers purs voisins (Camera, NodeGeometry, PanelScroll, Tooltip.place,
                // WireLayer.distanceToCurve…), c'est ce qui reste ici qui est du dessin.
                exclude("fr/blueprint/client/editor/BlueprintEditorScreen*")
                exclude("fr/blueprint/client/editor/UnsavedChangesScreen*")
                exclude("fr/blueprint/client/editor/CanvasWidget*")
                exclude("fr/blueprint/client/editor/NodeWidget*")
                exclude("fr/blueprint/client/editor/WireLayer*")
                exclude("fr/blueprint/client/editor/GridLayer*")
                exclude("fr/blueprint/client/editor/Minimap*")
                exclude("fr/blueprint/client/editor/Tooltip*")
                exclude("fr/blueprint/client/editor/ToolbarWidget*")
                exclude("fr/blueprint/client/editor/DetailsPanel")
                exclude("fr/blueprint/client/editor/VariablePanel*")
                exclude("fr/blueprint/client/editor/DiagnosticsPanel*")
                exclude("fr/blueprint/client/editor/PalettePopup*")
                exclude("fr/blueprint/client/editor/RegistryPickerPopup*")
                exclude("fr/blueprint/client/editor/RegistryCatalog*")
                exclude("fr/blueprint/client/editor/ScriptView")
                exclude("fr/blueprint/client/editor/ScriptView$*")
                // Concepteur d'écrans (10.2) : même partage qu'ailleurs — l'état pur
                // (ScreenCanvasController, AlignmentGuides, DesignSurface,
                // ElementPropertiesState) est testé headless, seul le dessin sort.
                exclude("fr/blueprint/client/editor/screen/ScreenDesignerWidget*")
                exclude("fr/blueprint/client/editor/screen/ModeTabs*")
                // Le HUD (10.9) : HudView est testé, le peintre et la couche de rendu non.
                exclude("fr/blueprint/client/screen/ScreenPainter*")
                exclude("fr/blueprint/client/screen/BlueprintScreen*")
                exclude("fr/blueprint/client/screen/BlueprintHud*")
                exclude("fr/blueprint/client/screen/TextureCache*")
                // Navigateur (F6) : BrowserState est testé, l'écran ne se teste pas.
                exclude("fr/blueprint/client/browser/BlueprintBrowserScreen*")
            })
            limit {
                // Mesuré à 0,83 : un point de marge, assez pour ne pas casser sur une
                // ligne, pas assez pour laisser passer une fonctionnalité non testée.
                minimum = "0.82".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
