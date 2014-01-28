#!/bin/sh
#
# The script to perform ablation runs on the 4 wikification dataset
# 
#
cpath="dist/wikifier-3.0-jar-with-dependencies.jar"

datafolder="data/WikificationACL2011Data"

configsFolder="configs/Ablation"

mkdir -p  output/Wikipedia
mkdir -p  output/AQUAINT
mkdir -p  output/MSNBC
mkdir -p  output/ACE

for configFile in `find $configsFolder -type f -printf "%f\n"`
do

	configpath="$configsFolder/$configFile"
	echo "Using config file $configpath"
	outDir="output/AblationResults/$configFile/"
    mkdir -p $outDir
    
    command="java -Xmx10g -jar $cpath -referenceAssistant"
    
    echo $command
    
	$command $datafolder/WikipediaSample/ProblemsTest/  $datafolder/WikipediaSample/RawTextsTest/ output/Wikipedia/   $configpath > $outDir/WikiTest& #Wikipedia
	$command $datafolder/AQUAINT/Problems/ $datafolder/AQUAINT/RawTexts/  output/AQUAINT/ $configpath > $outDir/AQUAINT & #AQUAINT
	$command $datafolder/MSNBC/Problems/ $datafolder/MSNBC/RawTextsSimpleChars/ output/MSNBC/   $configpath > $outDir/MSNBC &  #MSNBC
	$command $datafolder/ACE2004_Coref_Turking/Dev/ProblemsNoTranscripts/ $datafolder/ACE2004_Coref_Turking/Dev/RawTextsNoTranscripts output/ACE/  $configpath > $outDir/Ace & #ACE
	echo "Waiting for $configFile to finish"
	wait
	echo "Finished $configFile"
done
