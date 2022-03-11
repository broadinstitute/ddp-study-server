import * as XLSX from "xlsx";
import { RxNormDrugOption } from "./models";
import { parseToHOCON, writeFile } from "./utils/common";

const fileName = "RxTerms202202.txt";

const workBook = XLSX.readFile(fileName);

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
  }: "${current.optionLabelTemplate.templateText.replace(
    /[^a-zA-Z0-9 ]/g,
    ""
  )}"}},`;
  return (prev += stringBuilder);
}, "");

writeFile(
  "output.txt",
  JSON.stringify(parseToHOCON(`picklistOptions : [${picklistString}]`), null, 2)
);
