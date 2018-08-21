import { smlmFile, supported_text_formats, supported_image_formats, manifest_template } from './smlm_file';

const selected_file = '../../data/test_localization_table.smlm';

const smlm = new smlmFile();
smlm.import_smlm(selected_file).then((files)=>{
  console.log('smlm file loaded: ', files)
  console.log('manifest: ', smlm.manifest)
}).catch((e)=>{
  console.error(e)
});
