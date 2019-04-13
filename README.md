# scream-android

scream-android is a simple [Scream](https://github.com/duncanthrax/scream) receiver for Android

## Compile

You need Android SDK.

Run `./gradlew build` command then install the APK or open the project in Android Studio and click run.

## Usage

Android doesn't support more than 16bit for samples size. Make sure to set scream accordingly.

You can use any sample rate and number of channels (1 to 8) but remember than the higher those values the higher is the required bandwidth.

This receiver support multicast, but I strongly recommend unicast if you are experiencing bad quality.
