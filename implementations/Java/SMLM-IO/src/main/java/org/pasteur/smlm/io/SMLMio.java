/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pasteur.smlm.io;


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
public class SMLMio {
    
    
    
    public final static String type_uint8 = "uint8";
    public final static String type_uint16 = "uint16";
    public final static String type_uint32 = "uint32";
    public final static String type_float32 = "float32";
    public final static String type_float64 = "float64";
    
    public final static Hashtable<String, Integer> dtype2Byte = new Hashtable<String, Integer>() {{ put(type_uint8, 1); put(type_uint16, 2); put(type_uint32, 4);  put(type_float64, 8);  put(type_float32, 4); }};
    
    
    
   
    public SMLMio(){
        
        
        
        
        
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
                                    if (units==null){
                                        format.addUnit(headers[i].toString(), "a.u.");
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
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                try{
                                    System.out.println("file name: "+(String)n.get("name"));
                                    ZipEntry zipFileEntry = zip.getEntry((String)n.get("name"));
                                    InputStream is=zip.getInputStream(zipFileEntry);
                                    final int bufferSize = 2048;
                                    byte[] buffer = new byte[bufferSize];
                                    
                                    while (true) {
                                        int read = is.read(buffer);
                                        if (read == -1) {
                                           break;
                                        }
                                        baos.write(buffer,0,read);
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
                                
                                for (int i=0;i<hLen;i++){
                                    String head=headers[i].toString();
                                    if (i>0){
                                        elementPosit[i]=elementPosit[i-1]+nb[i-1]*format.getShape(head);
                                    }
                                    
                                    dataType[i]=dtype[i].toString();
                                    nb[i]=dtype2Byte.get(dtype[i]);
                                    
                                    lineSize+=nb[i]*format.getShape(head);
                                    
                                }
                                
                                String s;
                                byte [] bytes = baos.toByteArray();
                                int lineNumber=Integer.parseInt(n.get("rows").toString());
                                float previ=0;
                                for (int i=0;i<hLen;i++){
                                    
                                    String head=headers[i].toString();
                                    Object [] valueList = new Object[lineNumber*format.getShape(head)];
                                    
                                    switch ((String)dtype[i]) {
                                        case type_uint8:{
                                            byte min=Byte.MAX_VALUE;
                                            byte max=Byte.MIN_VALUE;
                                            double avg=0;
                                            byte value;
                                            for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    value=bytes[t*lineSize+elementPosit[i]+ii*nb[i]];
                                                    valueList[t*format.getShape(head)+ii]=value;
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
                                            table.addVector(head, valueList);
                                            table.setMin(head,(Object)min);
                                            table.setMax(head,(Object)max);
                                            table.setAverage(head,avg);
                                            break;
                                        }
                                        case type_uint16:{
                                            short min=Short.MAX_VALUE;
                                            short max=Short.MIN_VALUE;
                                            double avg=0;
                                            short value;
                                            for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    value=ByteBuffer.wrap(Arrays.copyOfRange(bytes, t*lineSize+elementPosit[i]+ii*nb[i], t*lineSize+elementPosit[i]+(ii+1)*nb[i])).order(ByteOrder.LITTLE_ENDIAN).getShort();
                                                    valueList[t*format.getShape(head)+ii]=value;
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
                                            table.addVector(head, valueList);
                                            table.setMin(head,(Object)min);
                                            table.setMax(head,(Object)max);
                                            table.setAverage(head,avg);
                                            break;
                                        }
                                        case type_uint32:{
                                            int min=Integer.MAX_VALUE;
                                            int max=Integer.MIN_VALUE;
                                            double avg=0;
                                            int value;
                                            for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    value=ByteBuffer.wrap(Arrays.copyOfRange(bytes, t*lineSize+elementPosit[i]+ii*nb[i], t*lineSize+elementPosit[i]+(ii+1)*nb[i])).order(ByteOrder.LITTLE_ENDIAN).getInt();
                                                    valueList[t*format.getShape(head)+ii]=value;
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
                                            table.addVector(head, valueList);
                                            table.setMin(head,(Object)min);
                                            table.setMax(head,(Object)max);
                                            table.setAverage(head,avg);
                                            break;
                                        }
                                        case type_float32:{
                                            float min=Float.POSITIVE_INFINITY;
                                            float max=Float.NEGATIVE_INFINITY;
                                            double avg=0;
                                            float value;
                                            float prev=0;
                                            float prevt=lineNumber;
                                            float previi=format.getShape(head);
                                            
                                            lolo:for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    value=ByteBuffer.wrap(Arrays.copyOfRange(bytes, t*lineSize+elementPosit[i]+ii*nb[i], t*lineSize+elementPosit[i]+(ii+1)*nb[i])).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                                                    
                                                    valueList[t*format.getShape(head)+ii]=value;
                                                    avg+=value;
                                                    if (min>value){
                                                        min=value;
                                                    }
                                                    if (max<value){
                                                        max=value;
                                                    }
                                                    
                                                    if ((i==0)&&(ii==0)&&(t<300)){
                                                    }
                                                    prev=value;
                                                    prevt=t;
                                                    previi=ii;
                                                    previ=i;
                                                }
                                                
                                            }
                                            
                                            avg/=(double)(lineNumber*format.getShape(head));
                                            table.addVector(head, valueList);
                                            table.setMin(head,(Object)min);
                                            table.setMax(head,(Object)max);
                                            table.setAverage(head,avg);
                                            break;
                                        }
                                        case type_float64:{
                                            double min=Double.POSITIVE_INFINITY;
                                            double max=Double.NEGATIVE_INFINITY;
                                            double avg=0;
                                            double value;
                                            for (int t=0;t<lineNumber;t++){   
                                                for (int ii=0;ii<format.getShape(head);ii++){
                                                    value=ByteBuffer.wrap(Arrays.copyOfRange(bytes, t*lineSize+elementPosit[i]+ii*nb[i], t*lineSize+elementPosit[i]+(ii+1)*nb[i])).order(ByteOrder.LITTLE_ENDIAN).getDouble();
                                                    valueList[t*format.getShape(head)+ii]=value;
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
                                            table.addVector(head, valueList);
                                            table.setMin(head,(Object)min);
                                            table.setMax(head,(Object)max);
                                            table.setAverage(head,avg);
                                            break;
                                        }
                                        default: 
                                            System.out.println("data type not recognized "+dtype[i]);
                                            break;
                                    }
                                    
                                    
                                }
                                
                                
                                table.setXOffset(Double.parseDouble(((JSONObject)(n.get("offset"))).get("x").toString()));
                                table.setYOffset(Double.parseDouble(((JSONObject)(n.get("offset"))).get("x").toString()));
                                
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
            offset.put("x", data.getTable(tableKeys[i]).getXOffset());
            offset.put("y", data.getTable(tableKeys[i]).getXOffset());
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
                
                int sizeVect=0;
                if (header.length!=0){
                    sizeVect=table.getVector(header[0]).length/format.getShape(header[0]);
                }
                for (int ii=0;ii<header.length;ii++){//data type size computation
                    int dtype=dtype2Byte.get(format.getType(header[ii]));
                    offsetLine[ii]=lineSize;
                    lineSize+=format.getShape(header[ii])*dtype;
                    
                    
                }
                byte [] bufferdata = new byte[lineSize*sizeVect];
                
                for (int ii=0;ii<header.length;ii++){
                    int dtype=dtype2Byte.get(format.getType(header[ii]));
                    byte []  bbuf = new byte[dtype];
                    
                    Object [] vect = table.getVector(header[ii]);//get each vector
                    for (int t=0;t<vect.length;t+=format.getShape(header[ii])){//copy each element in buffer
                        
                        for (int tt=0;tt<format.getShape(header[ii]);tt++){
                            
                            switch (format.getType(header[ii])) {
                            case type_uint8:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).put((byte)vect[t+tt]).array();
                                break;
                            }
                            case type_uint16:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).putShort((short)vect[t+tt]).array();
                                break;
                            }
                            case type_uint32:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).putInt((int)vect[t+tt]).array();
                                break;
                            }
                            case type_float32:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).putFloat((float)vect[t+tt]).array();
                                break;
                            }
                            case type_float64:{
                                bbuf=ByteBuffer.allocate(dtype).order(ByteOrder.LITTLE_ENDIAN).putDouble((double)vect[t+tt]).array();
                                break;
                            }
                            default: 
                                System.out.println("data type not recognized "+format.getType(header[ii]));
                                break;
                            }
                            
                                
                            for (int b=0;b<dtype;b++){
                                bufferdata[t*lineSize+offsetLine[ii]+tt*dtype+b]=bbuf[b];
                            }
                            
                            //zipOut.write(bbuf.array(), t*lineSize+offsetLine[ii-1]+tt*dtype, dtype);
                            
                        }
                    }
                    
                    //System.out.println("okok "+header[ii]);
                    
                }
                
                zipOut.write(bufferdata, 0, lineSize*sizeVect);
                
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
        Hashtable<String, Object> min = new Hashtable<String, Object>();
        Hashtable<String, Object> max = new Hashtable<String, Object>();
        Hashtable<String, Double> avg = new Hashtable<String, Double>();
        Hashtable<String, Object []> tableDict = new Hashtable<String, Object []>();
        double xOffset=0;
        double yOffset=0;
        
        
        
        public Table(Format format){
            this.format=format;
        }
        
        
        
        public Format getFormat(){
            return format;
        }
        
        public Object getValue(String header,int line){
            assert (format.getShape(header)==1);
            return (tableDict.get(header))[line];
        }
        
        public void destroyValue(String header,int line){
            tableDict.get(header)[line]=null;
        }
        
        public double getValueInDouble(String header,int line){
            assert (format.getShape(header)==1);
            double result=0;
            switch (format.getType(header)) {
                case type_uint8:{
                    result=(double)((Byte)((tableDict.get(header))[line]));
                    break;
                }
                case type_uint16:{
                    result=(double)((Short)((tableDict.get(header))[line]));
                    break;
                }
                case type_uint32:{
                    result=(double)((Integer)((tableDict.get(header))[line]));
                    break;
                }
                case type_float32:{
                    result=(double)((Float)((tableDict.get(header))[line]));
                    break;
                }
                case type_float64:{
                    result=(double)((Double)((tableDict.get(header))[line]));
                    break;
                }
                default: 
                    System.out.println("data type not recognized "+format.getType(header));
                    break;
                }
            return result;
        }
        
        public int getValueInInteger(String header,int line){
            assert (format.getShape(header)==1);
            int result=0;
            switch (format.getType(header)) {
                case type_uint8:{
                    result=(int)((Byte)((tableDict.get(header))[line]));
                    break;
                }
                case type_uint16:{
                    result=(int)((Short)((tableDict.get(header))[line]));
                    break;
                }
                case type_uint32:{
                    result=(int)((Integer)((tableDict.get(header))[line]));
                    break;
                }
                case type_float32:{
                    result=(int)((Float)((tableDict.get(header))[line])).intValue();
                    break;
                }
                case type_float64:{
                    result=(int)((Double)((tableDict.get(header))[line])).intValue();
                    break;
                }
                default: 
                    System.out.println("data type not recognized "+format.getType(header));
                    break;
                }
            return result;
        }
        
        public Object getValue(String header,int row,int positionInShape){
            assert (format.getShape(header)!=1);
            return (tableDict.get(header))[row*format.getShape(header)+positionInShape];
        }
        
        public void addVector(String header,Object [] vect){
            tableDict.put(header, vect);
        }
        
        
        public String [] getHeaders(){
            return format.getKeys();
        }
        
        
        public Object [] getVector(String header){
            return (tableDict.get(header));
        }
        
        public double [] getVectorInDouble(String header){
            Object [] v=tableDict.get(header);
            double [] result = new double [v.length];
            switch (format.getType(header)) {
                case type_uint8:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(double)((Byte)v[i]);
                    }
                    break;
                }
                case type_uint16:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(double)((Short)v[i]);
                    }
                    break;
                }
                case type_uint32:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(double)((Integer)v[i]);
                    }
                    break;
                }
                case type_float32:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(double)((Float)v[i]);
                    }
                    break;
                }
                case type_float64:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(double)((Double)v[i]);
                    }
                    break;
                }
                default: 
                    System.out.println("data type not recognized "+format.getType(header));
                    break;
                }
            return result;
        }
        
        public int [] getVectorInInteger(String header){
            Object [] v=tableDict.get(header);
            int [] result = new int [v.length];
            switch (format.getType(header)) {
                case type_uint8:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(int)((Byte)v[i]);
                    }
                    break;
                }
                case type_uint16:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(int)((Short)v[i]);
                    }
                    break;
                }
                case type_uint32:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(int)((Integer)v[i]);
                    }
                    break;
                }
                case type_float32:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(int)((Float)v[i]).intValue();
                    }
                    break;
                }
                case type_float64:{
                    for (int i=0;i<v.length;i++){
                        result[i]=(int)((Double)v[i]).intValue();
                    }
                    break;
                }
                default: 
                    System.out.println("data type not recognized "+format.getType(header));
                    break;
                }
            return result;
        }
        
        public Object [] getVectorShape(String header,int row){
            return Arrays.copyOfRange(tableDict.get(header), row, row+format.getShape(header));
        }
        
        
        public void setMin(String header,Object min){
            this.min.put(header, min);
        }
        
        public void setMax(String header,Object max){
            this.max.put(header, max);
        }
        
        public void setAverage(String header,double avg){
            this.avg.put(header, avg);
        }
        
        public Object getMin(String header){
            return this.min.get(header);
        }
        
        public Object getMax(String header){
            return this.max.get(header);
        }
        
        public double getAverage(String header){
            return this.avg.get(header);
        }
        
        public void setXOffset(double x){
            this.xOffset=x;
        }
        
        public void setYOffset(double y){
            this.yOffset=y;
        }
        
        
        public double getXOffset(){
            return this.xOffset;
        }
        
        public double getYOffset(){
            return this.yOffset;
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
        
        