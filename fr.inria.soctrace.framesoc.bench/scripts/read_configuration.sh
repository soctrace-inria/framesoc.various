#!/bin/bash

echo "# OS details"
uname -a
echo "# HW details"
echo "Number of CPUs: "`cat /proc/cpuinfo | grep "model name" | wc -l`
echo "CPU information (all cpus are equal):"
echo "- "`grep "model name" /proc/cpuinfo | tail -1`
echo "- "`grep "cache size" /proc/cpuinfo | tail -1`
HT=`grep -o '^flags\b.*: .*\bht\b' /proc/cpuinfo | tail -1 | wc -l`
echo "- hyperthreading : "`if [ $HT -eq 1 ]; then echo "active"; else echo "not active"; fi` 
echo "Scaling governor: "`cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor`
echo "RAM: "`cat /proc/meminfo | grep "MemTotal" | awk '{print $2 " " $3}'`
