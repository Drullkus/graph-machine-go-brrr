# Haha graph machine go brrr
A modification for Minecraft that visualizes Minecraft's code. The code is organized into classes, which come from compiled Java files and are linked by references.

## Links
- GitHub: https://github.com/Drullkus/graph-machine-go-brrr
- CurseForge: https://www.curseforge.com/minecraft/mc-mods/graph-machine
- YouTube video: https://youtu.be/Y_xN-cmeVFg

## Data source
In a manner of speaking, the data is surveyed directly:

The graph data is generated from a `minecraft.jar`, currently targeting `build/moddev/artifacts/minecraft-patched-26.3.0.76.jar` which also includes NeoForge modifications. This file will exist after running `./gradlew --refresh-dependencies`.

Executing `./jdeps-minecraft.sh` generates two plaintext files; `jdeps-output/minecraft-patched-26.3.0.76.jar.dot` and `jdeps-output/summary.dot`.

`minecraft-patched-26.3.0.76.jar.dot` contains directional relationships in the form of classes depending on other classes, internal and external of the jar. Only the internal links are preserved and made bidirectional for the resulting graph.

The summary file contains class dependencies external to the jar and isn't as useful as the `minecraft-patched-26.3.0.76.jar.dot` file.

Scripts `./tools/jdeps_to_graph.py` then `./tools/layout_graph.py` process this data into a viewable graph in the form of coordinates.

The last step is `./gradlew runData`, which outputs the viewable graph as a `.json` file. Only the `.json` file is baked into the mod when compiled and none of the Shell, Python, nor datagen code are included in the CurseForge download.

## Viewing the graph in-game
1. Run `./gradlew runClient` from project root to launch the game with this mod installed. Or download from CurseForge to run from a client with NeoForge installed.
2. Open the World Creation Menu, click the "Game Mode" repeatedly until it says "Game Mode: Creative", and click the tab labeled "World" to access the World Type button.
3. Hold the `Alt` or `Option` key on your keyboard while clicking the World Type button, clicking repeatedly until the button says "World Type: Class Graph". 
4. With "World Type: Class Graph" activated, click the "New World" button to generate and enter the world with the class graph.

## Visualization options
Enter the Class Graph world with Creative or Spectator mode for optimal viewing. Flying your character around lets you get close and distant views of the graph.

The panel on the right side of the window gives you finer control:
- Filtering for nodes by class names, package names, or fully-qualified names.
- The filtered nodes has a further option to reveal their connected class nodes, and whether you see connections between those adjacent nodes.
- Showing packages that the class nodes belong to.
- Hiding the visualized edges between class nodes or merging node-to-node into cluster-to-cluster.
- Hiding each cluster of classes. Each are named after a common package.

Also, class node closest to your crosshairs will show the Java class's fully-qualified name above the Clusters subpanel.
