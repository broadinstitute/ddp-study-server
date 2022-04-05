import * as XLSX from "xlsx";
import { RxNormDrugOption } from "./models";
import { parseToHOCON, writeFile } from "./utils/common";

const inputFileName = process.argv[2] || "RxTerms202202.txt";
const outputFileName = process.argv[3] || "output.txt";

const translations: string[] =  process.argv.filter(arg => ['en', 'es', 'ru'].includes(arg));

const workBook = XLSX.readFile(inputFileName);

const workSheet = workBook.Sheets[workBook.SheetNames[0]];

const rxNormArray = XLSX.utils.sheet_to_json(workSheet) as RxNormDrugOption[];

const filteredRxNormOptions = rxNormArray.map((drug) => ({
  stableId: drug.RXCUI,
  displayName: drug.DISPLAY_NAME
}));

const pickListOptions = filteredRxNormOptions.map((option) => ({
  stableId: option.stableId,
  optionLabelTemplate: {
    templateType: "TEXT",
    templateText: option.displayName
  }
}));

const picklistString = pickListOptions.reduce((prev, current) => {
  const stringBuilder = `{${Object.keys(current)[0]}: ${
    current.stableId
  } optionLabelTemplate:{${Object.keys(current.optionLabelTemplate)[0]}: "${
    current.optionLabelTemplate.templateType
  }" ${
    Object.keys(current.optionLabelTemplate)[1]
  }: "$drug_name"
  variables: [
    {
      name: drug_name
      translations: [
         ${translations.map(lang => `{language:  ${lang}  text:  "${current.optionLabelTemplate.templateText.replace(/'/g, "")}"}` )}
      ]
    }
  ]  
}},`;
  return (prev += stringBuilder);
}, "");


writeFile(
  outputFileName,
  JSON.stringify(parseToHOCON(`picklistOptions : [${picklistString}]`), null, 2)
);
