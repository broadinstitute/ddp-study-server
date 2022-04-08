import * as XLSX from "xlsx";
import { RxNormDrugOption } from "./models";
import * as fs from "fs";

const inputFileName = process.argv[2] || "RxTerms202202.txt";
const outputFileName = process.argv[3] || "output.txt";
const MAX_LENGTH = 45;

const groupBy = <T, K>(list: T[], getKey: (item: T) => K) => {
  const map = new Map<K, T[]>();
  list.forEach((item) => {
    const key = getKey(item);
    const collection = map.get(key);
    if (!collection) {
      map.set(key, [item]);
    } else {
      collection.push(item);
    }
  });
  return Array.from(map.values());
};

const translations: string[] = process.argv.filter((arg) =>
  ["en", "es", "ru"].includes(arg)
);

const workBook = XLSX.readFile(inputFileName);

const workSheet = workBook.Sheets[workBook.SheetNames[0]];

const rxNormArray = XLSX.utils.sheet_to_json(workSheet) as RxNormDrugOption[];

const filteredRxNormsArray = groupBy(rxNormArray, (x) => x.DISPLAY_NAME).map(
  (group) => group[0]
);

const picklistOptions = filteredRxNormsArray.map((drug) => ({
  stableId: generateStableID(drug.DISPLAY_NAME),
  optionLabelTemplate: {
    templateType: "TEXT",
    templateText: "$drug_name",
    variables: [
      {
        name: "drug_name",
        translations: translations.map((lang) => ({
          language: lang,
          text: replaceSingleQuotes(drug.DISPLAY_NAME)
        }))
      }
    ]
  }
}));

function replaceSingleQuotes(text: string): string {
  return text.replace(/'/g, "");
}

function generateStableID(drugDisplayName: string): string {
  drugDisplayName = drugDisplayName
    .trimEnd()
    .replace(/[^a-zA-Z0-9 ]/g, "")
    .replaceAll(" ", "_")
    .toUpperCase();

  drugDisplayName = trimStringByMaxLength(drugDisplayName);

  drugDisplayName = removeFloatingUnderscoresAfterTrim(drugDisplayName);

  return drugDisplayName;
}

function removeFloatingUnderscoresAfterTrim(text: string): string {
  return text.charAt(text.length -1) === '_' ? text.substring(0, text.length - 1): text;
}

function trimStringByMaxLength(text: string): string {
  return text.length > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
}

fs.writeFile(
  outputFileName,
  JSON.stringify({ picklistOptions }, null, 2),
  (err) => {
    if (err) {
      return console.log(err);
    }
    console.log("The file was saved!");
  }
);
