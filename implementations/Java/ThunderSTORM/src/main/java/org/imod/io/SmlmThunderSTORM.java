/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.imod.io;

import ij.IJ;
import ij.Prefs;

import cz.cuni.lf1.lge.ThunderSTORM.ImportExport.IImportExport;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.MoleculeDescriptor;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.MoleculeDescriptor.Units;
import cz.cuni.lf1.lge.ThunderSTORM.results.GenericTable;
import cz.cuni.lf1.lge.ThunderSTORM.util.Pair;

import org.apache.commons.io.input.CountingInputStream;

import java.io.*;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;
import java.io.IOException;

/**
 * A template for processing each pixel of either
 * GRAY8, GRAY16, GRAY32 or COLOR_RGB images.
 *
 * @author Johannes Schindelin
 */
public class SmlmThunderSTORM implements IImportExport {
  @Override
  public String getName() {
      return "SMLM File";
  }
  @Override
  public String getSuffix() {
      return "smlm";
  }
    @Override
    public void importFromFile(String fp, GenericTable table, int startingFrame) throws IOException {
        assert(table != null);
        assert(fp != null);
        assert(!fp.isEmpty());





        SmlmFile smlmio = new SmlmFile();

        SmlmFile.Data d=smlmio.import_smlm(fp);

        String [] key = d.getTableKeys();
        if (key.length!=0){//read the first table

            SmlmFile.Table t=d.getTable(key[0]);
            String [] colnames=t.getHeaders();
            int colSize=colnames.length;
            if (colSize!=0){

                d.getTable(colnames[0]);
                int nrows=t.getNrows();

                SmlmFile.Format f = t.getFormat();
                Units [] colunits = new Units[colSize];
                for (int i=0;i<colSize;i++){
                    String unit = f.getUnit(colnames[i]);
                    colunits[i]=Units.fromString(unit);

                }

                if(!table.columnNamesEqual(colnames)) {
                    throw new IOException("Labels in the file do not correspond to the header of the table (excluding '" + MoleculeDescriptor.LABEL_ID + "')!");
                }

                double [] values;
                double [] offset=new double [colSize];
                for (int c=0;c<colSize;c++){
                    offset[c]=t.getOffset(colnames[c]);
                }
                int r = 0;
                int [][] index = new int [colSize][];
                for (int c=0,cc=0;c<colSize;c++){
                    index[c]=t.getColumnIndex(colnames[c]);
                }

                double [][] tableData = t.getTable();
                loop:for(int i=0;i<nrows;i++){

                    values = new double[colSize];
                    boolean ok;
                    for (int c=0,cc=0;c<colSize;c++){



                        values[cc] = tableData[i][index[c][0]];

                        if (Double.isNaN(values[cc])){
                            continue loop;
                        }
                        if(MoleculeDescriptor.LABEL_FRAME.equals(colnames[c])) {
                            values[cc] += startingFrame-1;
                        }

                        cc++;
                    }

                    if(table.isEmpty()) {
                        table.setDescriptor(new MoleculeDescriptor(colnames, colunits));
                    }
                    table.addRow(values);

                    IJ.showProgress((double)(r++) / (double)nrows);
                }
                table.insertIdColumn();
                table.copyOriginalToActual();
                table.setActualState();
            }
        }
    }

    @Override
    public void exportToFile(String fp, int floatPrecision, GenericTable table, List<String> columns) throws IOException {
        assert(table != null);
        assert(fp != null);
        assert(!fp.isEmpty());
        assert(columns != null);

        int nrows = table.getRowCount();

        SmlmFile smlmio = new SmlmFile();
        SmlmFile.Format format = smlmio.new Format();

        String name="Thunderstorm_localization_table";
        String nameBin="Thunderstorm_localization_table.bin";
        String nameFormat="smlm-table(binary)";

        for (int c=0;c<columns.size();c++){

            format.addUnit(columns.get(c), table.getColumnUnits(columns.get(c)).getLabel());
            format.addType(columns.get(c), (SmlmFile.type_float32));
            format.addShape(columns.get(c), 1);
        }

        format.addMeta_data("name", nameFormat);
        format.addMeta_data("mode", "binary");
        format.addMeta_data("type", "table");
        format.addMeta_data("extension", ".bin");
        format.addMeta_data("columns", columns.size());

        SmlmFile.Table tab = smlmio.new Table(format);

        double [][] tableData=new double[nrows][columns.size()];


        for (int c=0;c<columns.size();c++) {

            int [] index = new int [1];
            index[0]=c;
            tab.setIndex(columns.get(c),index);

            String column=columns.get(c);
            double [] th=table.getColumnAsDoubles(column);
            double min=Float.POSITIVE_INFINITY;
            double max=Float.POSITIVE_INFINITY;
            double avg=0;
            double v;
            for (int u=0;u<nrows;u++){
                tableData[u][c]= th[u];

                v=th[u];
                if (v<min){
                    min=v;
                }
                if (v>max){
                    max=v;
                }
                avg+=v;
            }
            avg/=(double)nrows;
            tab.setAverage(column, avg);
            tab.setMin(column, min);
            tab.setMax(column, max);


            IJ.showProgress((double)c / (double)columns.size());
        }

        tab.addTable(tableData);

        tab.addMeta_data("name", nameBin);
        tab.addMeta_data("format", nameFormat);
        tab.addMeta_data("rows", nrows);
        tab.addMeta_data("type", "table");





        SmlmFile.Data data = smlmio.new Data();
        data.format_version="0.2";
        data.addTable(nameBin, tab);

        smlmio.export_smlm(fp,data);
        IJ.showStatus("localization table saved");
    }

}
