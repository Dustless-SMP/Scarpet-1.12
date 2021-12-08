# Scarpet in 1.12
This is a fork of carpet mod 1.12, largely because I was too lazy to make my own mod, and plus it relies on some of the 
infrastructure of carpet mod (not much tho tbh).

## Credits

The code for this version of scarpet is basically a copy pasta of [the first scarpet's code](https://github.com/gnembon/carpetmod),
1.13.2, which is a lot easier to put into mc. The original plan was to use this, but then I found https://github.com/Advait-Sen/ScarpetInterpreter
which does the hard work for me.

Note that here we use a simplified fork of that (https://github.com/Dustless-SMP/ScarpetInterpreter), which doesn't contain so much of the mathsy stuff.

## Getting Started
### Setting up your sources
- Clone this repository.
- Run `gradlew setupCarpetmod` in the root project directory.

### Using an IDE
- To use Eclipse, run `gradlew eclipse`, then import the project in Eclipse.
- To use Intellij, run `gradlew idea`, then import the project in Intellij.

## Using the build system
Edit the files in the `src` folder, like you would for a normal project. The only special things you have to do are as follows:
### To generate patch files so they show up in version control
Use `gradlew genPatches`
### To apply patches after pulling
Use `gradlew setupCarpetmod`. It WILL overwrite your local changes to src, so be careful.
### To create a release / patch files
In case you made changes to the local copy of the code in `src`, run `genPatches` to update the project according to your src.
Use `gradlew createRelease`. The release will be a ZIP file containing all modified classes, obfuscated, in the `build/distributions` folder.
### To run the server locally (Windows)
Use `mktest.cmd` to run the modified server with generated patches as a localhost server. It requires `gradlew createRelease` to finish successfully as well as using default paths for your minecraft installation folder.

In case you use different paths, you might need to modify the build script.
This will leave a ready server jar file in your saves folder.

It requires to have 7za installed in your paths
