/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.imod.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.ListIterator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
/**
 *
 * @author benoit
 */
public class SmlmFile {
    
    
    
    public final static String type_uint8 = "uint8";
    public final static String type_uint16 = "uint16";
    public final static String type_uint32 = "uint32";
    public final static String type_float32 = "float32";
    public final static String type_float64 = "float64";
    
    public final static Hashtable<String, Integer> dtype2Byte = new Hashtable<String, Integer>() {{ put(type_uint8, 1); put(type_uint16, 2); put(type_uint32, 4);  put(type_float64, 8);  put(type_float32, 4); }};
    
    
    
   
    public SmlmFile(){
        
        
        
        
        
    }
    
    
    
    public Data import_smlm(String file_path){
        
        
        
        try{
            long t1 = System.currentTimeMillis();
            ZipFile zip = new ZipFile(file_path);
            ZipEntry zipEntry = zip.getEntry("manifest.json");
            if (zipEntry!=null){
                
                
                JSONParser parser = new JSONParser();
                
                try {
                    InputStreamReader isr = new InputStreamReader(zip.getInputStream(zipEntry));
                    Object obj = parser.parse(isr);
                    
                    JSONObject manifest = (JSONObject) obj;
                    
                    assert ((Double)manifest.get("format_version")==0.2);
                    
                    Data data = new Data();
                    
                    Hashtable<String,Format> formats = new Hashtable<String,Format>();
                    
                    data.setFormatVersion((String)manifest.get("format_version"));
                    
                    Object [] info=manifest.keySet().toArray();
                    for (int i=0;i<info.length;i++){
                        if (!(((String)info[i]).equals("files")||((String)info[i]).equals("formats")||((String)info[i]).equals("format_version"))){
                            data.addMeta_data((String)info[i], manifest.get(info[i]).toString());
                        }
                    }
                    
                    
                    
                    JSONArray file_info = (JSONArray) manifest.get("files");
                    Iterator<JSONObject> iterator = file_info.iterator();
                    
                    while (iterator.hasNext()) {
                        
                        JSONObject n = iterator.next();
                        if (n.get("type").equals("table")){
                            System.out.println("loading table...");
                            
                            
                            
                            String format_key = (String) n.get("format");
                            JSONObject formatsJ = (JSONObject) manifest.get("formats");
                            JSONObject file_format = (JSONObject) formatsJ.get(format_key);
                            
                            Object [] headers = (((JSONArray) file_format.get("headers")).toArray());
                            Object [] dtype = (((JSONArray) file_format.get("dtype")).toArray());
                            Object [] shape = (((JSONArray) file_format.get("shape")).toArray());
                            Object [] units = null ;
                            try{
                                units = (((JSONArray) file_format.get("units")).toArray());
                            }catch(Exception e){System.out.println("Warning: units not set");}
                            
                            assert ((headers.length == dtype.length) && dtype.length == shape.length);
                            
                            
                            
                            Format format;
                            if (formats.containsKey(format_key)){
                                format=formats.get(format_key);
                            }
                            else{
                                format = new Format();
                                for (int i=0;i<headers.length;i++){
                                    format.addShape(headers[i].toString(), Integer.parseInt(shape[i].toString()));
                                    format.addType(headers[i].toString(), dtype[i].toString());
                                    if ((units==null)||(units.length==0)){
                                        if ((headers[i].toString().equals("x"))||(headers[i].toString().equals("y"))||(headers[i].toString().equals("z"))){
                                            format.addUnit(headers[i].toString(), "nm");
                                        }
                                        else{
                                            format.addUnit(headers[i].toString(), "");
                                        }
                                    }
                                    else{
                                        format.addUnit(headers[i].toString(), units[i].toString());
                                    }
                                }
                                Object [] infoFormat=file_format.keySet().toArray();
                                for (int i=0;i<infoFormat.length;i++){
                                    if (!(((String)infoFormat[i]).equals("headers")||((String)infoFormat[i]).equals("dtype")||((String)infoFormat[i]).equals("shape")||((String)infoFormat[i]).equals("units"))){
                                        format.addMeta_data((String)infoFormat[i], file_format.get(infoFormat[i]));
                                    }
                                }
                            }
                            
                            
                            
                            
                            Table table = new Table(format);
                            
                            if (file_format.get("mode").equals("binary")){
                                long t0 = System.currentTimeMillis();
                                ArrayList<ByteArrayOutputStream> albaos = new ArrayList<ByteArrayOutputStream>();
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                try{
                                    
                                    ZipEntry zipFileEntry = zip.getEntry((String)n.get("name"));
                                    InputStream is=zip.getInputStream(zipFileEntry);
                                    int bufferSize = 2048*512;
                                    byte[] buffer = new byte[bufferSize];
                                    long number=0;
                                    long maximumNumber=2000000000;//around max of integer
                                    int id=0;
                                    boolean ok=true;
                                    while (true) {
                                        int read = is.read(buffer);
                                        if (read == -1) {
                                           break;
                                        }
                                        baos.write(buffer,0,read);
                                        number+=bufferSize;
                                        if (number>maximumNumber){
                                            albaos.add(baos);
                                            baos = new ByteArrayOutputStream();
                                            number=0;
                                            ok=true;
                                        }
                                        else{
                                            ok=false;
                                        }
                                     }
                                    if (!ok){
                                        albaos.add(baos);
                                    }
                                    
                                }
                                catch(Exception keyError){
                                    System.out.println("ERROR while loading "+(String)n.get("name")+" in zip file");
                                    continue;
                                }
                                
                                
                                int hLen = format.getSize();
                                
                                int [] elementPosit = new int [hLen];//position of variables in a line
                                int [] nb = new int [hLen];
                                String [] dataType = new String [hLen];
                                int lineSize = 0;//size of 1 line
                                elementPosit[0]=0;
                                int tableColumnNumber=0;
                                
                                for (int i=0,ii=0;i<hLen;i++){
                                    
                                    String head=headers[i].toString();
                                    if (i>0){
                                        elementPosit[i]=elementPosit[i-1]+nb[i-1]*format.getShape(head);
                                    }
                                    
                                    dataType[i]=dtype[i].toString();
                                    nb[i]=dtype2Byte.get(dtype[i]);
                                    
                                    lineSize+=nb[i]*format.getShape(head);
                                    tableColumnNumber+=format.getShape(head);
                                    int [] index = new int[format.getShape(head)];
                                    for (int iii=0;iii<format.getShape(head);iii++){
                                        index[iii]=ii++;
                                    }
                                    table.setIndex(head, index);
                                }
                                
                                String s;
                                
                                
                                int lineNumber=Integer.parseInt(n.get("rows").toString());
                                
                                double [][] valueTable=new double [lineNumber][tableColumnNumber];
                                
                                
                                byte [][] bytes = new byte[albaos.size()][];
                                long [] indexBytes = new long[albaos.size()];
                                for (int a=0;a<albaos.size();a++){
                                    bytes[a]=albaos.get(a).toByteArray();
                                    
                                    if (a==0){
                                        indexBytes[a]=bytes[a].length;
                                    }
                                    else{
                                        indexBytes[a]=indexBytes[a-1]+bytes[a].length;
                                    }
                                }
                                
                                int a=0;
                                int b=0;
                                int c=0;
                                                    
                                for (int i=0;i<hLen;i++){
                                    
                                    String head=headers[i].toString();
                                    int [] index = table.getColumnIndex(head);
                                    //double [] valueList = new double[lineNumber*format.getShape(head)];
                                    double min=Double.POSITIVE_INFINITY;
                                    double max=Double.NEGATIVE_INFINITY;
                                    double avg=0;
                                    double value;
                                    switch ((String)dtype[i]) {
                                        case type_uint8:{
                                            for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    a=0;
                                                    b=t*lineSize+elementPosit[i]+ii*nb[i];
                                                    if (albaos.size()>1){
                                                        loop:for (int aa=0;aa<albaos.size();aa++){
                                                            if ((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]<indexBytes[aa]){
                                                                a=aa;
                                                                break loop;
                                                            }
                                                            else{
                                                                b=(int)((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]-indexBytes[aa]);
                                                            }
                                                        }
                                                    }
                                                    value=bytes[a][b];
                                                    valueTable[t][index[ii]]=value;
                                                    avg+=value;
                                                    if (min>value){
                                                        min=value;
                                                    }
                                                    if (max<value){
                                                        max=value;
                                                    }
                                                }
                                            }
                                            avg/=(double)(lineNumber*format.getShape(head));
                                            break;
                                        }
                                        case type_uint16:{
                                            for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    a=0;
                                                    b=t*lineSize+elementPosit[i]+ii*nb[i];
                                                    if (albaos.size()>1){
                                                        loop:for (int aa=0;aa<albaos.size();aa++){
                                                            if ((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]<indexBytes[aa]){
                                                                a=aa;
                                                                break loop;
                                                            }
                                                            else{
                                                                b=(int)((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]-indexBytes[aa]);
                                                            }
                                                        }
                                                    }
                                                    value=ByteBuffer.wrap(Arrays.copyOfRange(bytes[a], b, b+nb[i])).order(ByteOrder.LITTLE_ENDIAN).getShort();
                                                    valueTable[t][index[ii]]=value;
                                                    avg+=value;
                                                    if (min>value){
                                                        min=value;
                                                    }
                                                    if (max<value){
                                                        max=value;
                                                    }
                                                }
                                            }
                                            break;
                                        }
                                        case type_uint32:{
                                            for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    a=0;
                                                    b=t*lineSize+elementPosit[i]+ii*nb[i];
                                                    if (albaos.size()>1){
                                                        loop:for (int aa=0;aa<albaos.size();aa++){
                                                            if ((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]<indexBytes[aa]){
                                                                a=aa;
                                                                break loop;
                                                            }
                                                            else{
                                                                b=(int)((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]-indexBytes[aa]);
                                                            }
                                                        }
                                                    }
                                                    value=ByteBuffer.wrap(Arrays.copyOfRange(bytes[a], b, b+nb[i])).order(ByteOrder.LITTLE_ENDIAN).getInt();
                                                    valueTable[t][index[ii]]=value;
                                                    avg+=value;
                                                    if (min>value){
                                                        min=value;
                                                    }
                                                    if (max<value){
                                                        max=value;
                                                    }
                                                    
                                                    
                                                }
                                                
                                            }
                                            break;
                                        }
                                        case type_float32:{
                                            lolo:for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    a=0;
                                                    b=t*lineSize+elementPosit[i]+ii*nb[i];
                                                    if (albaos.size()>1){
                                                        loop:for (int aa=0;aa<albaos.size();aa++){
                                                            if ((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]<indexBytes[aa]){
                                                                a=aa;
                                                                break loop;
                                                            }
                                                            else{
                                                                b=(int)((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]-indexBytes[aa]);
                                                            }
                                                        }
                                                    }
                                                    value=ByteBuffer.wrap(Arrays.copyOfRange(bytes[a], b, b+nb[i])).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                                                    
                                                    valueTable[t][index[ii]]=value;
                                                    avg+=value;
                                                    if (min>value){
                                                        min=value;
                                                    }
                                                    if (max<value){
                                                        max=value;
                                                    }
                                                    
                                                }
                                                
                                            }
                                            break;
                                        }
                                        case type_float64:{
                                            for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    a=0;
                                                    b=t*lineSize+elementPosit[i]+ii*nb[i];
                                                    if (albaos.size()>1){
                                                        loop:for (int aa=0;aa<albaos.size();aa++){
                                                            if ((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]<indexBytes[aa]){
                                                                a=aa;
                                                                break loop;
                                                            }
                                                            else{
                                                                b=(int)((long)t*(long)lineSize+(long)elementPosit[i]+(long)ii*(long)nb[i]-indexBytes[aa]);
                                                            }
                                                        }
                                                    }
                                                    value=ByteBuffer.wrap(Arrays.copyOfRange(bytes[a], b, b+nb[i])).order(ByteOrder.LITTLE_ENDIAN).getDouble();
                                                    valueTable[t][index[ii]]=value;
                                                    avg+=value;
                                                    if (min>value){
                                                        min=value;
                                                    }
                                                    if (max<value){
                                                        max=value;
                                                    }
                                                }
                                            }
                                            
                                            break;
                                        }
                                        default: 
                                            System.out.println("data type not recognized "+dtype[i]);
                                            break;
                                    }
                                    avg/=(double)(lineNumber*format.getShape(head));
                                    table.addTable(valueTable);
                                    table.setMin(head,min);
                                    table.setMax(head,max);
                                    table.setAverage(head,avg);
                                    
                                    
                                    if (((JSONObject)(n.get("offset"))).containsKey(head)){
                                        table.setOffset(head,Double.parseDouble(((JSONObject)(n.get("offset"))).get(head).toString()));
                                    }
                                }
                                
                                
                                
                                Object [] infoTable=n.keySet().toArray();
                                for (int i=0;i<infoTable.length;i++){
                                    if (!(((String)infoTable[i]).equals("min")||((String)infoTable[i]).equals("max")||((String)infoTable[i]).equals("avg")||((String)infoTable[i]).equals("offset"))){
                                        table.addMeta_data((String)infoTable[i], n.get(infoTable[i]));
                                        
                                    }
                                }
                                
                                
                                
                            }
                            data.addTable((String)n.get("name"), table);
                        }
                        if (n.get("type").equals("image")){
                            
                            
                            
                            
                            
                            
                            
                        }
                        
                    }
                    isr.close();
                    
                    //data.log();
                    long t2 = System.currentTimeMillis();
                    System.out.println("SMLM file loaded in "+(t2-t1)/1000.+" seconds");
                    return data;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }    
                
                
                
            }
            else{
                throw new RuntimeException("invalid file: no manifest.json found in the smlm file");
            }
            
        
        
        
            
        } catch(IOException e){
            throw new RuntimeException("Error unzipping file " + file_path, e);
            
        }
        return null;
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    public void export_smlm(String file_path, Data data){
        
        JSONObject formats = new JSONObject();
        
        JSONObject manifest = new JSONObject();
        String [] metaKeys=data.getMeta_dataKeys();
        manifest.put("format_version", data.format_version);
        for (int i=0;i<metaKeys.length;i++){
            manifest.put(metaKeys[i],data.getMeta_data(metaKeys[i]));
        }
        String [] tableKeys=data.getTableKeys();
        JSONArray tables = new JSONArray();
        for (int i=0;i<tableKeys.length;i++){//for each table
            
            
            
            Format format=data.getTable(tableKeys[i]).getFormat();
            String formatName= format.getMeta_data("name").toString();
            String [] header = format.getKeys();
            if (!formats.containsKey(formatName)){//if frame is new
                JSONObject formatJson = new JSONObject();
                String [] metaFormatKeys = format.getMeta_dataKeys();
                for (int ii=0;ii<metaFormatKeys.length;ii++){
                    formatJson.put(metaFormatKeys[ii],format.getMeta_data(metaFormatKeys[ii]));
                }
                JSONArray listHeader = new JSONArray();
                JSONArray listShape = new JSONArray();
                JSONArray listType = new JSONArray();
                JSONArray listUnits = new JSONArray();
                for (int ii=0;ii<header.length;ii++){
                    listHeader.add(header[ii]);
                    listShape.add(format.getShape(header[ii]));
                    listType.add(format.getType(header[ii]));
                    listUnits.add(format.getUnit(header[ii]));
                }
                formatJson.put("headers", listHeader);
                formatJson.put("dtype", listType);
                formatJson.put("units", listUnits);
                formatJson.put("shape", listShape);
                formats.put(formatName, formatJson);
            }
            
            JSONObject tableJson = new JSONObject();
            String [] metaTableKeys = data.getTable(tableKeys[i]).getMeta_dataKeys();
            for (int ii=0;ii<metaTableKeys.length;ii++){
                tableJson.put(metaTableKeys[ii],data.getTable(tableKeys[i]).getMeta_data(metaTableKeys[ii]));
                
            }
            JSONObject offset = new JSONObject();
            String [] offsetHeader=data.getTable(tableKeys[i]).getOffsetHeaders();
            for (int h=0;h<offsetHeader.length;h++){
                offset.put(offsetHeader[h], data.getTable(tableKeys[i]).getOffset(offsetHeader[h]));
            }
            tableJson.put("offset", offset);
            JSONObject min = new JSONObject();
            JSONObject max = new JSONObject();
            JSONObject avg = new JSONObject();
            for (int ii=0;ii<header.length;ii++){
                min.put(header[ii], data.getTable(tableKeys[i]).getMin(header[ii]));
                max.put(header[ii], data.getTable(tableKeys[i]).getMax(header[ii]));
                avg.put(header[ii], data.getTable(tableKeys[i]).getAverage(header[ii]));
            }
            tableJson.put("min", min);
            tableJson.put("max", max);
            tableJson.put("avg", avg);
            tables.add(tableJson);
            
            //SAVE BINARY FILE HERE
            
        }
        manifest.put("formats",formats);
        
        manifest.put("files", tables);

        
        try{
            
            
            
            
            
            
            FileOutputStream fos = new FileOutputStream(file_path);
            
            ZipOutputStream zipOut = new ZipOutputStream(fos);
            
            ZipEntry zipEntry = new ZipEntry("manifest.json");
            
            zipOut.putNextEntry(zipEntry);
            
            byte [] result = (manifest.toJSONString()).getBytes();
            final int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            for (int id=0;id<result.length;id+=bufferSize) {
                buffer=Arrays.copyOfRange(result, id, Math.min(id+bufferSize, result.length));
                
                zipOut.write(buffer, 0, Math.min(bufferSize,result.length-id));
            }
            
            
            
            for (int i=0;i<tableKeys.length;i++){//for each table
                ZipEntry zipEntryNext = new ZipEntry(tableKeys[i]);
                zipOut.putNextEntry(zipEntryNext);
                String [] header=data.getTable(tableKeys[i]).getHeaders();
                Table table = data.getTable(tableKeys[i]);
                Format format=table.getFormat();
                int lineSize=0;
                int [] offsetLine=new int [header.length];
                
                int sizeVect=table.getNrows();
                
                
                for (int ii=0;ii<header.length;ii++){//data type size computation
                    int dtype=dtype2Byte.get(format.getType(header[ii]));
                    offsetLine[ii]=lineSize;
                    lineSize+=format.getShape(header[ii])*dtype;
                    
                }
                long totalSize=(long)lineSize*(long)sizeVect;
                long tmp=totalSize;
                int maxNumber=2000000000;//close to max integer
                int numb=0;
                
                while(tmp>0){
                    tmp-=maxNumber;
                    numb++;
                }
                byte [][] bufferdata = new byte[numb][];
                long [] bufferdataIndex = new long[numb];
                for (int it=0;it<numb;it++){
                    if (it==0){
                        bufferdataIndex[it]=Math.min((long)totalSize, (long)maxNumber);
                    }
                    else{
                        bufferdataIndex[it]=bufferdataIndex[it-1]+Math.min((long)totalSize, (long)maxNumber);
                    }
                    bufferdata[it]=new byte[(int)Math.min((long)totalSize, (long)maxNumber)];
                    totalSize-=maxNumber;
                    
                }
                double [][] tableData=table.getTable();
                
                for (int ii=0;ii<header.length;ii++){
                    int dtype=dtype2Byte.get(format.getType(header[ii]));
                    byte []  bbuf = new byte[dtype];
                    
                    int  [] index = table.getColumnIndex(header[ii]);
                    
                    for (int t=0,ttt=0;ttt<sizeVect;ttt++,t+=format.getShape(header[ii])){//copy each element in buffer
                        
                        for (int tt=0;tt<format.getShape(header[ii]);tt++){
                            
                            switch (format.getType(header[ii])) {
                            case type_uint8:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).put((byte)tableData[ttt][index[tt]]).array();
                                break;
                            }
                            case type_uint16:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).putShort((short)tableData[ttt][index[tt]]).array();
                                break;
                            }
                            case type_uint32:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).putInt((int)tableData[ttt][index[tt]]).array();
                                break;
                            }
                            case type_float32:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).putFloat((float)tableData[ttt][index[tt]]).array();
                                break;
                            }
                            case type_float64:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).putDouble((double)tableData[ttt][index[tt]]).array();
                                break;
                            }
                            default: 
                                System.out.println("data type not recognized "+format.getType(header[ii]));
                                break;
                            }
                            
                                
                            for (int b=0;b<dtype;b++){
                                if (bufferdata.length==1){
                                    bufferdata[0][t*lineSize+offsetLine[ii]+tt*dtype+b]=bbuf[b];
                                }
                                else{
                                    int a=0;
                                    int posit=t*lineSize+offsetLine[ii]+tt*dtype+b;
                                    loop:for (int it=0;it<bufferdata.length;it++){
                                        if ((long)t*(long)lineSize+(long)offsetLine[ii]+(long)tt*(long)dtype+(long)b<bufferdataIndex[it]){
                                            a=it;
                                            break loop;
                                        }
                                        else{
                                            posit=(int)((long)t*(long)lineSize+(long)offsetLine[ii]+(long)tt*(long)dtype+(long)b-bufferdataIndex[it]);
                                        }
                                    }
                                    bufferdata[a][posit]=bbuf[b];
                                }
                            }
                            
                            //zipOut.write(bbuf.array(), t*lineSize+offsetLine[ii-1]+tt*dtype, dtype);
                            
                        }
                    }
                    
                    //System.out.println("okok "+header[ii]);
                    
                }
                for (int it=0;it<bufferdata.length;it++){
                    zipOut.write(bufferdata[it], 0, bufferdata[it].length);
                }
                
                
            }
            
            
            zipOut.close();
            fos.close();
            
        } catch(IOException e){
            throw new RuntimeException("Error unzipping file " + file_path, e);
            
        }
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    class Meta_data{
        
        
        Hashtable<String,Object> meta_data = new Hashtable<String,Object>();
        
        
        Meta_data(){
            
        }
        
        public void addMeta_data(String key,Object information){
            meta_data.put(key, information);
        }
        
        public Object getMeta_data(String key){
            return meta_data.get(key);
        }
        
        
        
        public String [] getMeta_dataKeys(){
            Object [] o=meta_data.keySet().toArray();
            String [] s = new String [o.length];
            for (int i=0;i<o.length;i++){
                s[i]=(String)o[i];
            }
            return s;
        }
        
        public void log(){
            String [] s = getMeta_dataKeys();
            for (int i=0;i<s.length;i++){
                if (meta_data.get(s[i]).toString().length()<80){
                    System.out.println(""+s[i]+": "+meta_data.get(s[i]).toString());
                }
                else{
                    System.out.println(""+s[i]+": "+meta_data.get(s[i]).toString().substring(0, 100)+"...");
                }
            }
        }
    
    }
    
    
    
    
    
    
    
    
    public class Data extends Meta_data{
        
        public String format_version;
        Hashtable<String,Table> tables = new Hashtable<String,Table>();
        //Hashtable<String,Image> images = new Hashtable<String,Image>();
        
        public Data(){
            
        }
        
        
        
        public void addTable(String key,Table table){
            tables.put(key, table);
        }
        
        public Table getTable(String key){
            return tables.get(key);
        }
        
        public String [] getTableKeys(){
            Object [] o=tables.keySet().toArray();
            String [] s = new String [o.length];
            for (int i=0;i<o.length;i++){
                s[i]=(String)o[i];
            }
            return s;
        }
        
        
        
        
        /*public void addImage(String key,Image image){
            images.put(key, image);
        }
        
        public Image getImage(String key){
            return images.get(key);
        }
        
        public String [] getImageKeys(){
            Object [] o=images.keySet().toArray();
            String [] s = new String [o.length];
            for (int i=0;i<o.length;i++){
                s[i]=(String)o[i];
            }
            return s;
        }*/
        
        
        public String getFormatVersion(){
            return this.format_version;
        }
        
        public void setFormatVersion(String v){
            this.format_version=v;
        }
        
        
        
        public void log(){
            System.out.println("DATA LIST:");
            System.out.println("FORMAT VERSION: "+this.format_version);
            super.log();
            String [] t1 = getTableKeys();
            for (int i=0;i<t1.length;i++){
                
                this.tables.get(t1[i]).log();
            }
            
            /*String [] t2 = getImageKeys();
            for (int i=0;i<t2.length;i++){
                
                this.images.get(t2[i]).log();
            }*/
            
        }
    }
    
    
    
    
    
    public class Format extends Meta_data{
        
        Hashtable<String,Integer> shapes = new Hashtable<String,Integer>();
        Hashtable<String,String> dtypes = new Hashtable<String,String>();
        Hashtable<String,String> units = new Hashtable<String,String>();
        
        
        
        public Format(){
        }
        
        
        
        public void addShape(String key,int shape){
            shapes.put(key, shape);
        }
        
        public int getShape(String key){
            return shapes.get(key);
        }
        
        public void addType(String key,String dtype){
            dtypes.put(key, dtype);
        }
        
        public String getType(String key){
            return dtypes.get(key);
        }
        
        public void addUnit(String key,String unit){
            units.put(key, unit);
        }
        
        public String getUnit(String key){
            return units.get(key);
        }
        
        public String [] getKeys(){
            Object [] o=shapes.keySet().toArray();
            String [] s = new String [o.length];
            for (int i=0;i<o.length;i++){
                s[i]=(String)o[i];
            }
            return s;
        }
        
        public boolean containsKey(String key){
            Object [] o=shapes.keySet().toArray();
            for (int i=0;i<o.length;i++){
                if (((String)o[i]).equals(key)){
                    return true;
                }
            }
            return false;
        }
        
        public int getSize(){
            return shapes.size();
        }
        
        
        public void log(){
            
            String [] s=getKeys();
            System.out.println("(format):");
            super.log();
            for (int i=0;i<s.length;i++){
                System.out.println(""+s[i]+" ->   shape:"+shapes.get(s[i])+"   type:"+dtypes.get(s[i])+"   unit:"+units.get(s[i]));
            }
            
        }
        
        
    
    }
    
    
    
    
    
    
    
    
    
    
    
    public class Table extends Meta_data{
        
        
        
        
        
        //TABLE FORMAT
        Format format;
        Hashtable<String, Double> min = new Hashtable<String, Double>();
        Hashtable<String, Double> max = new Hashtable<String, Double>();
        Hashtable<String, Double> avg = new Hashtable<String, Double>();
        Hashtable<String, Double> offset = new Hashtable<String, Double>();
        double [][] tableDict;
        Hashtable<String, int []> tableindex = new Hashtable<String, int []>();
        
        
        public Table(Format format){
            this.format=format;
        }
        
        
        
        public Format getFormat(){
            return format;
        }
        
        public void setIndex(String header,int [] index){
            tableindex.put(header, index);
        }
        
        
        public int getColumnIndex(String header, int shape){
            return tableindex.get(header)[shape];
        }
        
        public int [] getColumnIndex(String header){
            return tableindex.get(header);
        }
        
        
        public int getShapeSize(String header){
            return tableindex.get(header).length;
        }
        
        public void addTable(double [][] table){
            
            this.tableDict=table;
        }
        
        public double [][] getTable(){
            
            return tableDict;
        }
        
        public int getNrows(){
            return tableDict.length;
        }
        
        public int getNcolumns(){
            if (getNrows()>0)
                return tableDict[0].length;
            else
                return 0;
        }
        
        public String [] getHeaders(){
            return format.getKeys();
        }
        
        
        
        
        
        public void setMin(String header,double min){
            this.min.put(header, min);
        }
        
        public void setMax(String header,double max){
            this.max.put(header, max);
        }
        
        public void setAverage(String header,double avg){
            this.avg.put(header, avg);
        }
        
        public double getMin(String header){
            return this.min.get(header);
        }
        
        public double getMax(String header){
            return this.max.get(header);
        }
        
        public double getAverage(String header){
            return this.avg.get(header);
        }
        
        
        
        public String [] getOffsetHeaders(){
            Object [] o=offset.keySet().toArray();
            String [] s = new String [o.length];
            for (int i=0;i<o.length;i++){
                s[i]=(String)o[i];
            }
            return s;
        }
        
        public void setOffset(String header,double x){
            offset.put(header, x);
        }
        
        
        
        
        public double getOffset(String header){
            if (offset.containsKey(header))
                return offset.get(header);
            else
                return 0;
        }
        
        
        
        
        
        public void log(){
            
            String [] s=getHeaders();
            System.out.println("TABLE:");
            format.log();
            super.log();
            for (int i=0;i<s.length;i++){
                System.out.println(""+s[i]+" ->   min:"+getMin(s[i])+"   max:"+getMax(s[i])+"   avg:"+getAverage(s[i]));
            }
            System.out.println("");
            
            
        }
    
    }
    
    
    
    
    
    
}
        
        
    
