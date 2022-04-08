import * as XLSX from "xlsx";
import { RxNormDrugOption } from "./models";
import * as fs from 'fs';

const inputFileName = process.argv[2] || "RxTerms202202.txt";
const outputFileName = process.argv[3] || "output.txt";

const translations: string[] =  process.argv.filter(arg => ['en', 'es', 'ru'].includes(arg));

const workBook = XLSX.readFile(inputFileName);

const workSheet = workBook.Sheets[workBook.SheetNames[0]];

const rxNormArray = XLSX.utils.sheet_to_json(workSheet) as RxNormDrugOption[];

const picklistOptions = rxNormArray.map((drug) => ({
  stableId: drug.RXCUI.toString(),
  optionLabelTemplate: {
      templateType: 'TEXT',
      templateText: '$drug_name',
      variables: [{
          name: 'drug_name',
          translations: translations.map(lang => ({ language: lang, text: drug.DISPLAY_NAME.replace(/'/g, '') }))
      }]
  }
}));

fs.writeFile(outputFileName, JSON.stringify({picklistOptions}, null, 2), err => {
  if (err) {
    return console.log(err);
  }
  console.log("The file was saved!");
});