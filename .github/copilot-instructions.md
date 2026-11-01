# Copilot instructions for StoragePortal

## Project context

- This is a Java 25 Hytale server plugin with a bundled asset pack. Use the Gradle wrapper, not a globally installed Gradle.
- Java sources live in `src\main\java\net\marcelto\hytale`. The plugin entry point is `StoragePortal`.
- Assets and the plugin manifest live in `src\main\resources`. Portal models, textures, and idle animations are in `Common\Blocks\StoragePortal`; item definitions are in `Server\Item\Items`; UI is in `Common\UI\Custom\Pages`.
- Read `build.gradle`, `gradle.properties`, the manifest, and the affected sources before making changes. Use their current configuration rather than assuming README names or external examples are up to date.
- Use Windows paths and PowerShell commands in this checkout. Preserve the case and separators used inside Hytale asset references.

## Useful skills

- Load `hytale-modder` for plugin lifecycle, ECS, interactions, inventory, custom UI, and asset integration work. Read only the references needed for the task.
- Load `hytale-blockbench-generator` for model, texture, icon, and `.blockyanim` generation. Reuse its generator, preview, and animation references instead of inventing another format or renderer.
- These skills may be installed as personal skills rather than checked into this repository. Discover available skills by name; do not hardcode a contributor's home directory or assume a skill script exists in this checkout. If a required skill is unavailable, say so.
- Check the actual Hytale API or exporter source when examples conflict with the installed version. Do not guess method signatures, animation fields, or asset schemas.

## Implementation expectations

- Make complete, focused changes. Follow existing helpers, package structure, nullability annotations, logging, and Google Java Format. Avoid wildcard imports.
- Preserve server-authoritative inventory amounts and transfer behavior. Do not trust item counts or identifiers supplied by the UI without resolving them against current server state.
- Respect ECS store access, valid `Ref` checks, world-thread execution, chunk loading, and shutdown cleanup. Do not retain component objects across asynchronous work.
- Preserve the portal's closed idle, opening, open idle, and closing behavior, including multiple viewers and saved chunk restoration. Keep continuous visual animation client-side rather than adding server-side per-frame updates.
- Before changing an asset, inspect its item definition, model, texture, animation targets, and related states. Update references that the requested change requires, but do not silently change unrelated icons, hitboxes, scale, or `IconProperties`.
- Update directly affected README descriptions when behavior changes. Do not change gameplay limits, recipes, permissions, or compatibility requirements unless the task calls for it.

## Reproducible asset generation

- Keep model, texture, animation, icon, and preview scripts in `generate_blockbench\`, not loose in the repository root. Run Python scripts with `uv run` as directed by the generator skill.
- Resolve paths relative to the script or repository and use deterministic random seeds. Generate models and their texture atlases together so UV layouts stay consistent.
- Treat the user's current Blockbench edits as input. Compare geometry, node names, hierarchy, pivots, transforms, and UVs with the generator before regenerating. Integrate requested manual adjustments into the generator; ask before overwriting unexplained differences.
- Preserve animated node names and attachment points unless explicitly changing them. Check local versus parent transforms, face orientation, visible front and back faces, and clipping instead of compensating with arbitrary offsets.
- Preserve the existing atlas dimensions and texel density unless the change requires otherwise. Do not force existing assets into a generic 64x64 atlas.
- Match Hytale's chunky, hand-painted pixel-art style, readable silhouettes, limited material palettes, and restrained noise. Use original artwork.
- `generate_blockbench\` is currently ignored by Git. Keeping a script there does not make it version-controlled. Do not force-add generators or change ignore rules without an explicit request.

## Preview expectations

- After a visual asset change, render and inspect front, three-quarter, back, and side views plus an enlarged texture. Check missing faces, flipped UVs, muddy details, clipping, and scale.
- Show the actual preview image to the user, not only a filesystem path. If image display or inspection is unavailable, state that limitation.
- When implementing a preview script, default its output to the directory containing the script and provide an explicit output-directory override such as `--out`.
- For agent inspection runs, use that override to write temporary previews to the session artifacts directory. Do not put scratch images in the shipped asset directories.
- A static preview does not validate animation playback. Check loop seams, UV scrolling, targeted node names, keyframe ordering and bounds, and normalized quaternion values. Inspect playback in Blockbench with the Hytale plugin when available.
- Distinguish generator checks, software previews, Blockbench inspection, headless server validation, and in-game checks. Never claim the game or animation playback was verified when only JSON, a build, or a still image was checked.

## Build and validation

Run commands from the repository root:

| Command | Purpose |
| --- | --- |
| `.\gradlew.bat compileJava checkstyleMain spotlessCheck` | Targeted compilation, Java lint, and formatting checks. |
| `.\gradlew.bat check` | Run the project's verification tasks, including existing tests. |
| `.\gradlew.bat build` | Verify and package the plugin and resources into `build\libs`. |
| `.\gradlew.bat spotlessApply` | Apply Google Java Format when needed; inspect the diff for unrelated formatting changes. |
| `.\gradlew.bat updatePluginManifest` | Regenerate the manifest after intentional metadata changes. |
| `.\gradlew.bat prepareRunServer` | Prepare the local development server. |
| `.\gradlew.bat runServer -Dhotswap=true` | Start the development server when runtime testing is requested. |

- Use the smallest relevant check first and build when code or shipped resources change. Documentation-only changes do not require a Gradle build.
- For asset generation, also check valid JSON, unique node IDs, finite values, normalized quaternions, texture bounds, and animation-to-model node references. A successful Gradle build alone does not prove Hytale assets load or render correctly.
- Report blockers and remaining manual checks plainly. Do not start a long-lived server, deploy a JAR, or overwrite a user's game files without a request.
- Do not run `just reset` or delete development worlds, backups, or caches as a shortcut for validation.

## Git and workspace safety

- Work on the user's current branch. Preserve their uncommitted changes and do not create branches, stage, commit, push, amend, reset, or stash without an explicit request.
- If the user requests a commit, do not add Copilot as a co-author or collaborator.
- Never read or expose `.env` secrets as task context. Do not embed credentials, machine-specific installation paths, or private data in code or documentation.
