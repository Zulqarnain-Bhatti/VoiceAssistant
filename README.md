# Voice Assistant — Android (Kotlin)

Yeh project aapke Python voice assistant ka Android version hai, Kotlin mein likha gaya.

## Kya kaam karta hai
- Mic tap karke bolna → SpeechRecognizer se text banta hai
- TextToSpeech se assistant jawab boलता hai
- Commands: time, date, day, wikipedia search, google search, app open karna,
  volume up/down, mute/unmute, weather (Open-Meteo API), calculator, battery status,
  call dialer kholna, settings kholna, jokes

## Jo cheezein Windows version mein thi lekin Android mein possible nahi / alag hain
- `shutdown`, `restart`, `sleep`, `lock screen`, `task manager kill process` — yeh
  OS-level admin commands hain, normal Android app inhe nahi kar sakta (security ki wajah se)
- Keyboard/mouse simulation (`pyautogui` wala type/click/scroll) — mobile par is
  ka koi seedha equivalent nahi hai
- Brightness control (wmi wala) — Android par alag permission (`WRITE_SETTINGS`) chahiye,
  abhi shamil nahi kiya
- Flashlight ka function CommandProcessor.kt mein placeholder hai — CameraManager
  se wire karna hoga agar chahiye

## Is project ko APK banane ka tareeqa
1. **Android Studio** install karein (agar nahi hai): https://developer.android.com/studio
2. Yeh poora `VoiceAssistant` folder Android Studio mein **Open** karein
   (File → Open → is folder ko select karein)
3. Gradle sync khud ho jayega (internet chahiye hoga dependencies download karne ke liye)
4. Upar menu se **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK file yahan milegi: `app/build/outputs/apk/debug/app-debug.apk`
6. Yeh APK apne phone mein transfer karke install kar lein (Unknown sources allow karna hoga)

## Note
Maine yahan se seedha .apk file nahi bana sakta kyunke uske liye Android SDK,
Gradle build system, aur signing tools chahiye hote hain jo is environment mein
available nahi hain. Lekin yeh poora source code Android Studio mein khol kar
2-3 clicks mein APK ban jayega — upar wale steps follow karein.
