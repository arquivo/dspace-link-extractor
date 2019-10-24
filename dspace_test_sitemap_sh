while IFS="" read -r p || [ -n "$p" ]
do
	wget "$p/sitemap?map=0" > /dev/null 2>&1
	if [ $? -eq 0 ]; then
		echo "$p/sitemap?map=0"
	#else
		#echo "$p/sitemap?map=0 missing" 
	fi
	
done < rcaap_directory.txt
