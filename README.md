# Hybridious Mod
> *Created because AndrewH200 pulled out a glass bottle during a pillow fight...*

A feature-rich MeteorClient addon for Minecraft 1.21.4 that adds essential utilities for stuff.

## 📋 Requirements
- **Minecraft Version:** 1.21.4
- **MeteorClient:** Required
- **Fabric Loader:** Required

## 🚀 Features

### 🌱 AutoMoss
Automatically uses bonemeal on moss blocks and optionally azalea bushes for efficient terrain modification.

### 💣 B36 (Peacemaker)
Air bombing system using flint and TNT - perfect for making an impression on the locals.

### 👻 DeathExplore
Continue exploring even after death with this advanced exploration tool.

### 🚢 SethBoat
Advanced entity riding system with automatic rotation locking, designed for efficient travel on ice highways.

### 🚂 Minecart Detector
Detects and logs chested minecarts with direction and stacking analysis.
- Logs info to files located in "%APPDATA%/.minecraft" in the folders "STACKED_MINECARTS.txt" and "WRONG_DIRECTION_MINECARTS.txt"

### 🌿 LawnMower
Automatically mows lawns by breaking grass and other vegetation blocks, keeping your surroundings clean and tidy.

### 🔊 SoundOnSneak
Plays custom audio files when you sneak or on a random timer interval. Perfect for adding personality to your gameplay!
- **Sneak Trigger:** Play sounds when pressing the sneak key (can be toggled on/off)
- **Random Timer:** Automatically play sounds at random intervals
- **Custom Sounds:** Use your own WAV audio files
- **Volume Control:** Adjustable volume settings
- **Loop Options:** Choose to loop sounds while sneaking
- **Random Selection:** Pick random sounds from your folder

#### 🎵 Adding Custom Sounds to SoundOnSneak
1. Navigate to your Minecraft directory: `%APPDATA%\.minecraft\meteor-client\`
2. Create or locate the `hybridious_mod` folder
3. Place your `.wav` audio files in this folder
   - **Supported formats:** WAV, AIFF, AU (WAV recommended for best compatibility)
   - **File naming:** Use any filename (e.g., `sound.wav`, `meme.wav`, `notification.wav`)
4. Configure the module in MeteorClient:
   - Enable "random-sound" to play random files from the folder, OR
   - Set "sound-file" to specify a particular file to play
5. Adjust volume, timer intervals, and other settings to your preference

## 📦 Installation
1. Download and install [Fabric Loader](https://fabricmc.net/use/)
2. Install [MeteorClient](https://meteorclient.com/) for Minecraft 1.21.4
3. Download the latest release of Hybridious Mod from the [Releases](../../releases) page
4. Place the `.jar` file in your `mods` folder
5. Launch Minecraft with the Fabric profile

## 🎮 Usage
After installation, all modules will be available in your MeteorClient GUI. Access them through:
1. Open MeteorClient menu (Right Shift by default)
2. Navigate to the modules section
3. Find and configure Hybridious modules as needed

Each module comes with customizable settings to fit your playstyle and server requirements.

## ⚠️ Disclaimer
This mod is intended for use on servers where such modifications are permitted. Always check your server's rules before using any client-side modifications. The developers are not responsible for any consequences resulting from the use of this mod on servers where it may be prohibited.

## 🤝 Contributing
Contributions are welcome! Please feel free to:
- Submit bug reports
- Suggest new features
- Create pull requests
- Improve documentation

## 📄 License
This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the LICENSE file for detailed information.

## 🔗 Links
- [MeteorClient Official Website](https://meteorclient.com/)
- [Fabric Mod Loader](https://fabricmc.net/)
- [Hybridious_mod](https://github.com/Hybridious/hybridious_mod)

## 📝 Changelog
Check the [Releases](../../releases) page for detailed changelog information and download links.

---

## 🔍 Related Projects
**Third-Party Alternatives:**
- [AutoMoss for RusherHack](https://github.com/master7720/AutoMoss) - A third-party AutoMoss implementation for RusherHack based on this project's code (not affiliated with this project)
