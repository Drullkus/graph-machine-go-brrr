#!/bin/bash

# ensure folder exists
mkdir -p jdeps-output/

# use Java SE's built-in class dependency analyzer https://docs.oracle.com/javase/8/docs/technotes/tools/unix/jdeps.html
jdeps -verbose:class -dotoutput jdeps-output/ build/moddev/artifacts/minecraft-patched-26.3.0.76.jar

# read disk usage of files generated
du -h jdeps-output/*
