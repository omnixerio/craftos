chmod +x ./gradlew || exit 1
chmod +x ./OS/steps.sh || exit 1

./gradlew build || exit 1

cd OS/ || exit 1
sudo ./steps.sh
