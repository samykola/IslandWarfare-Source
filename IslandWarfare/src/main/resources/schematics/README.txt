Island Warfare - Schematics Folder
===================================

Place your WorldEdit .schem files here, one per island level, using the
exact file names configured in config.yml (island.levels.<level>.schematic):

  island-level-1.schem
  island-level-2.schem
  island-level-3.schem
  island-level-4.schem
  island-level-5.schem

How to create a schematic:
 1. Build your island template anywhere in a creative/build world.
 2. Select the full build area with WorldEdit (//pos1 and //pos2, or a wand).
 3. Decide on your paste origin: the plugin pastes each schematic centered
    on the island's grid slot at the Y level set by island.paste-y in
    config.yml. It's usually easiest to set your WorldEdit origin
    (//pos1) at the center-bottom of the build so it lines up neatly.
 4. Run //copy.
 5. Run //schem save island-level-<N>
 6. Copy the resulting .schem file from your server's
    plugins/WorldEdit/schematics/ folder into this
    plugins/IslandWarfare/schematics/ folder.

The plugin caches each schematic in memory after the first paste, so
having many clans on the same level template costs no extra disk I/O.
