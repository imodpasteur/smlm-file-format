# Data used to compare file sizes and loading times 
* ## The following 21 data sets were  selected randomly:

    |Samples   |DOI|
    |--------|:----|
    |1-4|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507641.svg)](https://doi.org/10.5281/zenodo.5507641)|
    |8,13|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507638.svg)](https://doi.org/10.5281/zenodo.5507638)|
    |15|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507634.svg)](https://doi.org/10.5281/zenodo.5507634)|
    |21|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507329.svg)](https://doi.org/10.5281/zenodo.5507329)|
    |25, 27|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507053.svg)](https://doi.org/10.5281/zenodo.5507053)|
    |34, 34-2|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507305.svg)](https://doi.org/10.5281/zenodo.5507305)|
    |35, 39|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507605.svg)](https://doi.org/10.5281/zenodo.5507605)|
    |42|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507679.svg)](https://doi.org/10.5281/zenodo.5507679)|
    |51-53|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507826.svg)](https://doi.org/10.5281/zenodo.5507826)|
    |57|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5507976.svg)](https://doi.org/10.5281/zenodo.5507976)|
    |64|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5508212.svg)](https://doi.org/10.5281/zenodo.5508212)|
    |69|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5508210.svg)](https://doi.org/10.5281/zenodo.5508210)|
    |71,73,74|[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.5508210.svg)](https://doi.org/10.5281/zenodo.5508210)|

* ## Under ThunderSTORM (version: [dev-2016-09-10-b1](https://github.com/zitmen/thunderstorm/releases/tag/dev-2016-09-10-b1)) folder, download SMLM File plugin (version: [v0.1.1](https://github.com/imodpasteur/smlm-file-format/releases/tag/v0.1.1))

    You should see "SMLM File" option when you are importing and exporting with ThunderSTORM
    ![Capture du 2022-06-13 16-23-43](https://user-images.githubusercontent.com/56833522/173398651-c93f00a1-fdfd-40d3-8321-14ccd8db23f9.png)


* ## Download corresponded data
* ## In Fiji(ImageJ) run macro script to export __*.csv__ format to __*.smlm__ and __*.tsf__

    ```java
    folder="Folder of downloaded data";
    savefolder=folder;


    list=getFileList(folder);
    for (i=0; i<list.length; i++) {
        if (endsWith(list[i], ".csv")){
                
            filename=folder+"/"+list[i];
            savename=savefolder+"/"+list[i];
            print(""+filename);
            print(""+filename.substring(0,filename.length-4)+".csv");
            run("Import results", "detectmeasurementprotocol=true   filepath="+filename+" fileformat=[CSV (comma separated)] livepreview=true rawimagestack= startingframe=1 append=false");
            run("Export results", "floatprecision=5 filepath="+savename.substring(0,savename.length-4)+".tsf fileformat=[Tagged spot file] sigma=true intensity=true chi2=true offset=true saveprotocol=false x=true y=true bkgstd=true id=true uncertainty=true frame=true detections=true");
            run("Export results", "floatprecision=5 filepath="+savename.substring(0,savename.length-4)+".smlm fileformat=[SMLM File] sigma=true intensity=true chi2=true offset=true saveprotocol=false x=true y=true bkgstd=true id=true uncertainty=true frame=true detections=true");

            run("Close All");
        }
    
    }
    ```

* ## Compute loading (import) time through ThunderStorm plugin

    ```java
    folder="Folder of downloaded and converted data";
    savefolder=folder;

    run("Close All");
    list=getFileList(folder);
    print("CellName,CSV,TSF,SMLM(ms)");
    for (i=0; i<list.length; i++) {
        if (endsWith(list[i], ".csv")){
                
            filename=folder+"/"+list[i];
            savename=savefolder+"/"+list[i];
            t0_tmp = getTime();
            run("Import results", "detectmeasurementprotocol=true filepath="+filename+" fileformat=[CSV (comma separated)] livepreview=true rawimagestack= startingframe=1 append=false");
            tcsv = getTime()-t0_tmp;
            run("Close All");
            //print("tsf");		
            t0_tmp = getTime();
            run("Import results", "detectmeasurementprotocol=true filepath="+filename.substring(0,filename.length-4)+".tsf"+" fileformat=[Tagged spot file] livepreview=true rawimagestack= startingframe=1 append=false");
            ttsf = getTime()-t0_tmp;
            run("Close All");
            //print("smlm");
            t0_tmp = getTime();
            run("Import results", "detectmeasurementprotocol=true filepath="+filename.substring(0,filename.length-4)+".smlm"+" fileformat=[SMLM File] livepreview=true rawimagestack= startingframe=1 append=false");
            tsmlm = getTime()-t0_tmp;
            IJ.log(""+list[i].substring(0,list[i].length-4)+","+tcsv+","+ttsf+","+tsmlm);
        }
    
    }

     ```
