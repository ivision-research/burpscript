#!/bin/sh


executable="$0"

langSet=0

LPY=Python
usePy=0

LJS=Js
useJs=0

usage() {
    printf "Usage: %s\n\n" "$executable"
    printf "Build the Burp plugin jar\n\n"
    printf "   -p/--python\tBuild with Python support\n"
    printf "   -j/--js\tBuild with JavaScript support\n"
}

for arg in "$@"; do
  shift
  case "$arg" in
    "--help")   set -- "$@" "-h"   ;;
    "--js")     set -- "$@" "-j"   ;;
    "--python") set -- "$@" "-p"   ;;
    *)          set -- "$@" "$arg" ;;
  esac
done


while getopts hjp opt;
do
    case $opt in
        p) langSet=1; usePy=1;;
        j) langSet=1; useJs=1;;
        h) usage; exit 0;;
        ?) usage; exit 2;;
    esac
done

if [ "$langSet" -eq "0" ]; then
  # Default to Python
  echo "Defaulting to Python only support"
  usePy=1
fi

args=""

if [ "$usePy" -eq "1" ]; then
    args="$args -Dburpscript.lang$LPY=on"
else
    args="$args -Dburpscript.lang$LPY=off"
fi

if [ "$useJs" -eq "1" ]; then
    args="$args -Dburpscript.lang$LJS=on"
else
    args="$args -Dburpscript.lang$LJS=off"
fi

args="$args shadowJar -x test"

if gradle $args; then
    ver=$(grep "pluginVersion =" build.gradle.kts | cut -d'=' -f2 | tr -d '" ')
    printf "JAR file can be found at build/libs/burpscript-plugin-%s.jar\n" "$ver"
fi
