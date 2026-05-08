# Modding Lib

## Description
Modding Lib is a modding library for Minecraft designed to facilitate the development of mods using Fabric.

## Information
- **Version** : alpha-1.0.10
- **Minecraft** : 1.21.4
- **Loader** : Fabric 0.17.2
- **Java** : 21

## Prerequisites
- **Java** : 21 or higher
- **Gradle** : 8.1 or higher
- **Minecraft** : 1.21.4 (client or server)

## Installation

### As a Dependency
Add the following to your `build.gradle`:

```gradle
repositories {
    maven { url 'https://maven.example.com/ModdingLib' }
}

dependencies {
    modImplementation 'org.triggersstudio:moddinglib:alpha-1.0.6'
}
```

## Getting Started

### Basic Usage
To use Modding Lib in your mod, import the library and start utilizing its UI components:

```java
import org.triggersstudio.moddinglib.ui.SliderWidget;
import org.triggersstudio.moddinglib.ui.TextInputWidget;
```

Check the documentation and examples in the source code for more details on available components.

## Building the Project

### Prerequisites
- Java 21 JDK installed
- Gradle installed

### Build Steps
```bash
# Clone the repository
git clone https://github.com/SAOFR-DEV/Modding-Lib.git
cd ModdingLib

# Build the project
./gradlew build

# Build and publish locally
./gradlew publishToMavenLocal
```

The compiled JAR files will be available in the `build/libs/` directory.

## Next Steps

**UI Components**
- [x] Add animation support for UIs
  > - [x] Spring family (default/snappy/bouncy/strong) — needs physics simulation
  > - [ ] Irregular family (light/heavy) — clarify spec
  > - [x] Loop / yoyo / onComplete callbacks
  > - [x] ColorTween (interpolate ARGB)
- [ ] Add video components for UIs
  > - [x] Phase 1 — VideoPlayer (FFmpeg via JavaCPP) + VideoComponent, video stream only, no audio yet
  > - [ ] Phase 2 — audio decode via OpenAL with A/V sync clock
  > - [ ] Phase 3 — seek / loop / hardware decode probe / direct sws_scale into NativeImage
- [x] Add progress bars for UIs
- [x] Add sliders for UIs
  >  - [x] Slider vertical (ajout d'un flag d'orientation).
  >  - [x] Support clavier (← → Home End) — nécessite un système de focus.
  >  - [x] Hover visuel sur le thumb.
  >  - [x] Coins arrondis sur bar/thumb/track — à faire quand Style gagnera un vrai cornerRadius.
- [ ] Add player render for UIs
- [x] Add text input for UIs
  > - [x] Add text area for UIs multi-lign, filter/max length, Tab cycle focus
  > - [x] Add onSubmit Enter
  > - [x] Add blink du caret
- [x] Add combo box for UIs
- [x] Add select list for UIs
- [x] Add color picker for UIs
- [x] Add toast notifications for UIs
- [x] Add tooltips for UIs
- [x] Add pagination for UIs
- [x] Add accordion for UIs
- [x] Add Skeleton support for UIs
- [x] Add spiner for UIs
- [x] Add calendar for UIs

**Other**
- [ ] Add channel communication to plugins

## License
This project is licensed under the MIT License. See the [LICENSE.md](LICENSE.md) file for details.

## Support & Issues
If you encounter any issues or have questions:
- [Open an issue](https://github.com/SAOFR-DEV/Modding-Lib/issues)
- Check existing issues and discussions
- Review the documentation in the source code

## Author
- [Perrier](https://github.com/PerrierBouteille)

## Contributors
*Be the first to contribute!*

## Contributing
Contributions are welcome! If you have any ideas or want to contribute to the project, please feel free to submit a pull request or open an issue on the GitHub repository.

### How to Contribute
1. Fork the repository
2. Create a new branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request
