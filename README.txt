JARVIS Android app - GitHub se APK banane ke steps (Termux se)

1) Termux mein:
   pkg install -y git unzip
   termux-setup-storage
   cd ~ && unzip /sdcard/Download/jarvis-app.zip && cd jarvis-app
   git config --global user.name "apna-naam"
   git config --global user.email "apna@email.com"

2) github.com par naya khali repo banayein (naam: jarvis).
   Token banayein: Settings > Developer settings > Personal access tokens (classic)
   Scopes: repo + workflow

3) Termux mein:
   git init -b main && git add -A && git commit -m "jarvis"
   git remote add origin https://github.com/USERNAME/jarvis.git
   git push -u origin main
   (password ki jagah token paste karein)

4) GitHub repo > Actions > "Build APK" > 5-8 minute baad > Artifacts > jarvis-apk
   Download karke zip kholein, app-debug.apk install karein.

5) App kholein: Mic, Phone permission allow karein. Settings mein API key daalein.
   "Gol button" dabayein, "Display over other apps" allow karein.
