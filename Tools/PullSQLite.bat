
adb shell run-as edu.uvawise.iris chmod 777 /data/data/edu.uvawise.iris/databases/
adb shell run-as edu.uvawise.iris cd '/data/data/edu.uvawise.iris/databases/' && ls"

adb shell run-as edu.uvawise.iris chmod 777 /data/data/edu.uvawise.iris/databases/Gmail

adb shell cp /data/data/edu.uvawise.iris/databases/Gmail /sdcard/

adb pull /sdcard/Gmail