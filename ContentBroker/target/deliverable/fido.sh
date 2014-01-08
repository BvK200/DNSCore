#!/bin/bash
PYTHON_PATH="PYTHON_BIN"
FIDO_PATH="fido/fido.py"

OUT=`$PYTHON_PATH $FIDO_PATH "$1" | grep "OK" | awk 'BEGIN {FS=",";};{printf $3",";}'` 

length=${#OUT}
printf ${OUT:0:(($length - 1))}
