function randId() {
     return Math.random().toString(36).substr(2, 10);
}

export class smlmFile {
  constructor(file) {
    this.manifest = JSON.parse(JSON.stringify(manifest_template))
    this.formats = this.manifest.formats
    this.files = this.manifest.files
    this.channels = {}
    this.zipFile = null
    this.uploaded = false
    this.isSMLM = false
    this.manifest.license = this.manifest.license || 'CC BY 4.0'
    this.manifest.tags = this.manifest.tags || []
    this.manifest.citeAs = this.manifest.citeAs || ''
  }
  import_image(file, image_format, info,  is_append, update_callback){
    return new Promise((resolve, reject) => {
      console.log('import file', image_format)
      var FR= new FileReader();
       FR.onload = (e) => {
          var img = new Image();
          img.addEventListener("load", ()=>{
            const file_info = {}
            file_info.format= image_format

            file_info.type = "image"
            file_info.size = [img.height, img.width]
            Object.assign(file_info, info)
            const data = {}
            data.image = img
            data.file = file
            data.name = file.name
            data.file_name = file.name
            data.file_size = file.size
            file_info.name = file.name
            file_info.data = data
            const hash = this.calculate_hash(file_info)
            file_info.hash = hash
            file_info.tags = info.tags || []
            file_info.pixel_size = info.pixel_size
            data.file_hash = hash

            if(is_append || !this.files || this.files.length==0){
              // append to this file
              this.files.push(file_info)
            }
            else{
              // reset files
              this.manifest = JSON.parse(JSON.stringify(manifest_template))
              this.formats = this.manifest.formats
              this.files = this.manifest.files
              this.files.push(file_info)
            }

            this.formats[image_format] = supported_image_formats[image_format]
            if(!this.manifest.name || this.manifest.name == ""){
              if(this.isSMLM){
                this.manifest.name = file.name.split(".")[0] + ".smlm"
              }
              else{
                this.manifest.name = file.name.split(".")[0] + ".zip"
              }

            }
            if(!this.manifest.hash)
            this.update_hash()
            info.channel = info.channel || 'default'
            this.channels[info.channel] = this.channels[info.channel] || []
            this.channels[info.channel].push(data)
            this.zipFile = null
            this.uploaded = false
            console.log(data)
            resolve(file_info)
            //context.drawImage(img, 0, 0);
          });
          img.src = e.target.result;
       };
       FR.onerror = (e) => {
         reject(e)
       }
       FR.readAsDataURL(file);
   })
  }
  import_text_table(file, table_format, info, is_append, update_callback){
    console.log(table_format)
    if(typeof table_format === 'string' || table_format instanceof String){
      table_format = supported_text_formats[table_format]
    }
    return new Promise((resolve, reject) => {
      if(!table_format){
        console.log("no table format not found.", table_format)
        reject("no table format not found.")
        return
      }
      var loadWorker;
      if (typeof (Worker) !== "undefined") {
          loadWorker = new Worker("static/js/AsyncTextLoader.js")
      } else {
          console.log("Web Worker not available")
          reject("Web Worker not available")
          return
      }
      console.log('loading...')
      update_callback( {running : true,  running_status: 'running '})

      loadWorker.onmessage = (e) => {
          var data = e.data
          if (data.error){
            reject(data.error)
            loadWorker.terminate()
          }
          else if (data.loaded) {
              // this.file_loaded = true
              console.log(data.tableDict)
              console.log('done', data.statshtml, data)
              const file_info = JSON.parse(JSON.stringify(file_info_template))

              data.name = file.name
              data.file_size = file.size
              file_info.name = file.name
              file_info.type = "table"
              file_info.rows = data.rows
              file_info.min = data.min
              file_info.max = data.max
              file_info.avg = data.avg
              file_info.offset = {"x": 0, "y": 0}
              file_info.data = data
              const hash = this.calculate_hash(file_info)
              file_info.hash = hash
              data.file_hash = hash
              file_info.tags = info.tags || []
              file_info.pixel_size = info.pixel_size
              if(is_append){
                // append to this file
                this.files.push(file_info)
              }
              else{
                // reset files
                this.manifest = JSON.parse(JSON.stringify(manifest_template))
                this.formats = this.manifest.formats
                this.files = this.manifest.files
                this.files.push(file_info)
              }
              if(!this.manifest.name || this.manifest.name == ""){
                this.manifest.name = file.name.split(".")[0] + ".smlm"
              }
              if(!this.manifest.hash)
              this.update_hash()
              info.channel = info.channel || 'default'
              this.channels[info.channel] = this.channels[info.channel] || []
              this.channels[info.channel].push(data)
              // this.formats[table_format.name] = table_format
              this.zipFile = null
              this.uploaded = false
              // only files with a table can count as smlm
              this.isSMLM = true
              loadWorker.terminate()
              console.log(data)
              resolve(file_info)
          }
          else if (data.progress){
              update_callback( {running_progress : data.progress,  running_status: (data.localization_num/1000000).toFixed(1) +'M localizations loaded'})
          }
      }
      loadWorker.onerror = () => {
        console.log('load error')
        reject('error occured during running.')
        loadWorker.terminate()
      }
      loadWorker.postMessage({file: file, format: table_format})
    });
   }
   import_smlm(file, is_append, update_callback){
    // TODO: support append
    if(is_append){
      throw "please load smlm file first, append mode is not supported yet."
    }
    return new Promise((resolve, reject) => {
      JSZip.loadAsync(file)
      .then((zip)=>{
        update_callback({running: true})
        const manifest_file = zip.file("manifest.json")
        if(!manifest_file){
          reject('manifest.json is not present in the zip file')
          return
        }

        manifest_file.async("string")
        .then((meta_json)=>{
          const manifest = JSON.parse(meta_json)
          const format_version = manifest.format_version
          update_callback({running_status: 'manifest file loaded.'})
          const data = {}
          const load_files = async () =>{
              //TODO: render the files with a different async loader
              for (let file_no=0; file_no<manifest.files.length; file_no++){
                await new Promise((resolve2, reject2) =>{
                  const file_info = manifest.files[file_no]
                  if(file_info.type == "table"){
                    update_callback({running_status: 'loading smlm file...'})
                    const table_file = zip.file(file_info.name)
                    const format_key = file_info.format
                    update_callback({running_status: 'uncompressing file: ' + file_info.name})
                    if(!table_file && manifest.formats[format_key]){
                      reject2('table file '+ manifest.table_file +' is not present in the zip file')
                      return
                    }
                    const format = manifest.formats[format_key]

                    table_file.async("blob")
                    .then((table_file)=>{
                      console.log(table_file)
                      const fileReader = new FileReader();
                      fileReader.onload = (evt) => {
                          update_callback({running_status: 'table loaded.'})
                          const arrayBuffer = evt.target.result
                          const uint8Arr = new Uint8Array(arrayBuffer)
                          let rows = file_info.rows
                          const columns = format.columns
                          for(let c=0;c<columns;c++){
                              if(format.headers[c].startsWith('_')){
                                format.headers[c] = format.headers[c].slice(1)
                              }
                          }
                          if(uint8Arr.length != rows * columns * 4){
                            console.error('file size mismatch: ', uint8Arr.length, rows * columns * 4)
                            reject2('file size does not match the manifest.')
                            return
                          }

                          const tableUint8Arrays = []
                          const tableArrays = []
                          for(let c=0;c<columns;c++){
                            tableUint8Arrays[c] = new Uint8Array(new ArrayBuffer(rows*4))
                          }
                          if(format_version == 1){
                            for(let c=0;c<columns;c++){
                              for(let r=0;r<rows*4;r++){
                                tableUint8Arrays[c][r] = uint8Arr[r*columns+c]
                              }
                            }
                          }
                          else{
                            for(let c=0;c<columns;c++){
                              for(let r=0;r<rows;r++){
                                tableUint8Arrays[c][4*r] = uint8Arr[r*columns*4+4*c]
                                tableUint8Arrays[c][4*r+1] = uint8Arr[r*columns*4+4*c+1]
                                tableUint8Arrays[c][4*r+2] = uint8Arr[r*columns*4+4*c+2]
                                tableUint8Arrays[c][4*r+3] = uint8Arr[r*columns*4+4*c+3]
                              }
                            }
                          }
                          const tableDict = {}
                          const tableUint8Dict = {}
                          for(let c=0;c<columns;c++){
                              tableArrays[c] = new Float32Array(tableUint8Arrays[c].buffer)
                              tableDict[format.headers[c]] = tableArrays[c]
                              tableUint8Dict[format.headers[c]] = tableUint8Arrays[c]
                          }

                          file_info.min = {}
                          file_info.max = {}
                          file_info.avg = {}
                          rows = tableArrays[0].length
                          for(let c=0;c<columns;c++){
                            const cn = format.headers[c]
                            file_info.min[cn] = Number.POSITIVE_INFINITY;
                            file_info.max[cn] = Number.NEGATIVE_INFINITY;
                            file_info.avg[cn] = 0;
                            for(let r=0;r<rows;r++){
                                if(file_info.min[cn]>tableArrays[c][r]){
                                  file_info.min[cn] = tableArrays[c][r]
                                  if(tableArrays[c][r] == 0){
                                    console.log(cn, c, r)
                                  }
                                }
                                if(file_info.max[cn]<tableArrays[c][r])
                                file_info.max[cn] = tableArrays[c][r]
                                file_info.avg[cn] +=tableArrays[c][r]/rows
                            }
                          }
                          data.headers = format.headers
                          for(let h=0;h<data.headers.length;h++){
                            data.headers[h] = data.headers[h].replace(/[^a-zA-Z0-9]+/g,'_').trim()
                          }
                          data.min = file_info.min
                          data.max = file_info.max
                          data.avg = file_info.avg
                          data.file_name = file_info.name
                          data.file_size = file.size
                          data.tableUint8Arrays = tableUint8Arrays
                          data.tableArrays = tableArrays
                          data.tableUint8Dict = tableUint8Dict
                          data.tableDict = tableDict
                          data.columns = columns
                          Object.assign(data, file_info)
                          file_info.data = data

                          file_info.data.integrity_test = false
                          const hash = this.calculate_hash(file_info)
                          if(!file_info.hash || file_info.hash!= hash){
                              file_info.data.integrity_test = false
                              console.log('integrity test failed: hash ' + file_info.hash + ' != ' + hash)
                          }
                          else{
                              file_info.data.integrity_test = true
                              console.log('integrity test passed: hash = ' + hash)
                          }
                          file_info.hash = hash
                          data.file_hash = hash

                          resolve2()
                      };
                      fileReader.onerror = (e) =>{
                        reject2(e)
                      }
                      fileReader.readAsArrayBuffer(table_file);
                    })
                    .catch((e)=>{
                      reject2(e)
                    })
                  }
                  else if(file_info.type == "image"){
                    update_callback({running_status: 'loading image file...'})

                    const format_key = file_info.format

                    // const format = manifest.formats[format_key]
                    const image_file = zip.file(file_info.name)
                    // console.log(image_file, format_key)
                    if(!image_file){
                      reject2('image file '+ manifest.image_file +' is not present in the zip file.')
                      return
                    }
                    update_callback({running_status: 'uncompressing file: ' + file_info.name})
                    image_file.async("blob")
                    .then((image_file)=>{

                      var FR= new FileReader();
                       FR.onload = (e) => {
                          var img = new Image();
                          img.addEventListener("load", ()=>{
                            const data = {}
                            data.image = img
                            data.name = file_info.name || file.name
                            data.file_name = file_info.name ||  file.name
                            data.file_size = image_file.size || file.size
                            data.file_hash = file_info.hash
                            file_info.data = data
                            resolve2()
                            //context.drawImage(img, 0, 0);
                          });
                          img.src = e.target.result;
                       };
                       FR.onerror = (e) => {
                         reject2(e)
                       }
                       FR.readAsDataURL(image_file);

                    })
                  }
                })
              }
          }
          load_files().then(()=>{
            this.formats = manifest.formats
            this.files = manifest.files
            this.manifest = manifest
            this.manifest.tags = this.manifest.tags || []
            this.update_hash()
            this.zipFile = null
            this.uploaded = false
            // only files with a table can count as smlm
            this.isSMLM = true
            resolve(this.files)
          })

        }).catch((e)=>{
          reject(e)
        })
      })
    });
  }
  calculate_hash(file_info){
    console.log('calculating md5 for ', file_info.name)
    const spark = new SparkMD5.ArrayBuffer()
    if(file_info.type == 'table'){
      const dict = file_info.data.tableDict
      if(dict.frame) spark.append(dict.frame.buffer)
      if(dict.x) spark.append(dict.x.buffer)
      if(dict.y) spark.append(dict.y.buffer)
    }
    else if (file_info.type == 'image'){
      const dataURI = file_info.data.image.src
      const ab = this.dataURItoArrayBuffer(dataURI)
      spark.append(ab)
    }
    const hash = spark.end()
    console.log(file_info.name, ' hash: ', hash)
    return hash
  }
  update_hash(){
    let hash = ''
    const spark = new SparkMD5()
    for(let file_no=0; file_no<this.files.length; file_no++){
      if(this.files[file_no].hash){
        spark.append(this.files[file_no].hash)
      }
      else{
        spark.append(this.files[file_no].name)
      }
    }
    this.manifest.hash = spark.end()
    console.log('update smlm file hash, name: ', this.manifest.name + ' md5: '+this.manifest.hash)
  }
  dataURItoArrayBuffer(dataURI) {
      const byteString = atob(dataURI.split(',')[1]);
      const mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0]
      const ab = new ArrayBuffer(byteString.length);
      const ia = new Uint8Array(ab);
      for (let i = 0; i < byteString.length; i++) {
         ia[i] = byteString.charCodeAt(i);
      }
      return ab;
  }
  dataURItoFile(dataURI, file_name) {
    const byteString = atob(dataURI.split(',')[1]);
    const mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0]
    const ab = new ArrayBuffer(byteString.length);
    const ia = new Uint8Array(ab);
    for (let i = 0; i < byteString.length; i++) {
       ia[i] = byteString.charCodeAt(i);
    }
    const blob = new Blob([ab], {type: mimeString});
    const name = file_name.split(".")[0]+"."+(mimeString.split('/')[1]||'png')
    return new File([blob], name);
  }
  save(update_callback){
  console.log('preparing .smlm file')
  return new Promise((resolve, reject) => {
    try {
      if(this.zipFile){
        resolve(this.zipFile)
        return
      }
      const zip = new JSZip()
      update_callback({running: true, running_progress:0, running_status:"Preparing a compressed smlm file..."})
      for(let file_no=0; file_no<this.files.length; file_no++){
          const file_info = this.files[file_no]
          if(file_info.type == "table"){
            // TODO: compare with existing format, if the same format already exist, reuse it.
            const format = JSON.parse(JSON.stringify(native_formats['smlm-table(binary)']))
            const format_key = format.name+ '-' + file_no
            const data = this.files[file_no].data
            const tableUint8Arrays = data.tableUint8Arrays
            const rows = tableUint8Arrays[0].length/4
            const columns = tableUint8Arrays.length

            format.name = format_key
            file_info.format = format_key
            this.formats[format_key] = format
            file_info.name = data.file_name.split(".")[0]+format.extension
            file_info.hash = this.calculate_hash(file_info)
            format.columns = columns
            for(let i=0;i<data.headers.length;i++){
              format.dtype[i] = "float32"
              format.shape[i] = 1
              format.headers[i] = data.headers[i].replace(/'/g,'').replace(/"/g,'').replace(/[^a-zA-Z0-9]+/g,'_').trim()
              if(format.headers[i].startsWith('_')){
                format.headers[i] = format.headers[i].slice(1)
              }
            }
            console.log('preparing table ', file_info.name)
            const buffer = new ArrayBuffer(rows*columns*4)
            const uint8Arr = new Uint8Array(buffer)
            console.log('binary file size: ' + rows*columns*4)
            // for(let c=0;c<columns;c++){
            //   for(let r=0;r<rows;r++){
            //     uint8Arr[r*columns+c] = tableUint8Arrays[c][r]
            //   }
            // }
            for(let c=0;c<columns;c++){
              for(let r=0;r<rows;r++){
                uint8Arr[r*columns*4+4*c] = tableUint8Arrays[c][4*r]
                uint8Arr[r*columns*4+4*c+1] = tableUint8Arrays[c][4*r+1]
                uint8Arr[r*columns*4+4*c+2] = tableUint8Arrays[c][4*r+2]
                uint8Arr[r*columns*4+4*c+3] = tableUint8Arrays[c][4*r+3]
              }
            }
            const blob = new Blob([uint8Arr]);
            const file = new File([blob], file_info.name, {type: "application/octet-stream", lastModified: Date.now()});
            console.log('file is ready.', file)
            console.log('preparing file for upload...')
            zip.file(file_info.name, file)
          }
          else if(file_info.type == 'image'){
            const data = this.files[file_no].data
            const dataURI = data.image.src
            const file = this.dataURItoFile(dataURI, data.file_name)
            file_info.hash = this.calculate_hash(file_info)
            // var mimeString = dataURI.split(',')[0].split(':')[1].split(';')[0]
            // file_info.name = data.file_name.split(".")[0]+"."+(mimeString.split('/')[1]||'png')
            //  var byteString = atob(dataURI.split(',')[1]);
            //  var ab = new ArrayBuffer(byteString.length);
            //  var ia = new Uint8Array(ab);
            //  for (var i = 0; i < byteString.length; i++) {
            //      ia[i] = byteString.charCodeAt(i);
            //  }
            //  var blob = new Blob([ab], {type: mimeString});
            zip.file(file.name, file)
          }
        }
        const manifest_ = Object.assign({}, this.manifest)
        manifest_.format_version = manifest_template.format_version
        const files_ = []
        for(let i=0;i<this.manifest.files.length;i++){
          const d = Object.assign({}, this.manifest.files[i])
          delete d.data
          files_.push(d)
        }
        manifest_.files = files_
        this.update_hash()
        console.log('smlm manifest: ', manifest_)
        zip.file("manifest.json", JSON.stringify(manifest_, null, "\t"))
        console.log('making zip archive...')
        zip.generateAsync({ type:"blob",
                            compression: "DEFLATE",
                            compressionOptions: {
                                level: 9
                            }},
                            (mdata)=>{
                              update_callback({running_status: "making zip files... ", running_progress:mdata.percent})
                            }
        ).then((zipBlob) => {
            const zip_file_name = this.manifest.name
            const zipFile = new File([zipBlob], zip_file_name, {type: "application/smlm", lastModified: Date.now()});
              console.log(zipFile)
              this.zipFile = zipFile
              resolve(zipFile)
              return
        }).catch((e)=>{
          console.error(e)
          reject(e)
          return
        })

      } catch (e) {
        console.error(e)
        reject(e)
        return
      }

    })
  }
}

export const supported_image_formats = {"png":{
    // Required
    "type": "image",
    "extension": ".png",
    "mode": "binary",
  },
  "jpg":{
      // Required
      "type": "image",
      "extension": ".jpg",
      "mode": "binary",
    },
  "jpeg":{
      // Required
      "type": "image",
      "extension": ".jpeg",
      "mode": "binary",
    }

}

export const supported_text_formats = {"ThunderSTORM(csv)": {
    // Required
    "type": "table",
    "extension": ".csv",
    "mode": "text",
    "dtype": "float32",
    "delimiter": ",",
    "comments": "",
    // specify which row is the header, set to -1 if there is no header
    "header_row": 0,
    // specify how to transform the headers to the standard header defined in smlm format,
    "header_transform": {"x [nm]":"x", "y [nm]":"y", "z [nm]":"z", "uncertainty_xy [nm]":"uncertainty_xy", "uncertainty_z [nm]":"uncertainty_z"},

    // Optional
    "name": "ThunderSTORM(csv)",
    "description": "a csv format used in thunderSTORM"
  },
  "ThunderSTORM(xls)": {
      // Required
      "type": "table",
      "extension": ".csv",
      "mode": "text",
      "dtype": "float32",
      "delimiter": "\t",
      "comments": "",
      // specify which row is the header, set to -1 if there is no header
      "header_row": 0,
      // specify how to transform the headers to the standard header defined in smlm format,
      "header_transform": {"x [nm]":"x", "y [nm]":"y", "z [nm]":"z", "uncertainty_xy [nm]":"uncertainty_xy", "uncertainty_z [nm]":"uncertainty_z"},

      // Optional
      "description": "a xls format used in thunderSTORM"
    }
}

export const native_formats = {
  "smlm-table(binary)": {
    // Required
    "name": "smlm-table(binary)",
    "type": "table",
    "mode": "binary",
    "extension": ".bin",
    "columns": 0,
    "headers": [],
    "dtype": [],
    "shape": [],
    "units": {},
    // Optional
    "name": "smlm-table(binary)",
    "description": "the default smlm format for localization tables"
  },
  "smlm-image(binary)": {
    // Required
    "type": "image",
    "mode": "binary",
    "extension": ".raw",
    "dtype": "uint32",
    "shape": [512, 512, null],
    "offset": 0,
    // Optional
    "name": "smlm-image(binary)",
    "description": "a format for 32-bit raw pixel image"
  },
  "smlm-table(csv)": {
    // Required
    "type": "table",
    "mode": "text",
    "extension": ".csv",
    "dtype": "float32",
    "delimiter": ",",
    "comments": "#",
    // specify which row is the header, set to -1 if there is no header
    "header_row": 0,
    // specify how to transform the headers to the standard header defined in smlm format,
    "header_transform": {},

    // Optional
    "name": "smlm-table(csv)",
    "description": "a format for export as csv format"
  }
}

export const manifest_template = {
  // Required
  "format_version": "0.2",

  "formats": {},

  "files": [],

  // Recommended
  "tags": [],
  "name": "",
  "description": "",
  // "thumbnail": "",
  "sample": "",
  "labeling": "",
  // "date": "",

  // Optional
  // "author": "",
  // "citation": "",
  // "email": "",
}

export const file_info_template = {
  //Required
  "name": "",
  "format": "smlm-table(binary)",
  "channel": "default",
  "rows": 0,
  // specify offsets for column(s), if need. set to {} if no offset needed.
  "offset": {},

  //Optional
  "metadata": {},
  "exposure": -1,
}
