chmod +x ./gradlew
chmod +x ./OS/steps.sh

./gradlew build

cd OS/
sudo ./steps.sh
