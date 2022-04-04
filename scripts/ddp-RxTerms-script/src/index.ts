import * as XLSX from "xlsx";
import { RxNormDrugOption } from "./models";
import { parseToHOCON, writeFile } from "./utils/common";
import * as inquirer from "inquirer";

const inquirerFileTreeSelection = require("inquirer-file-tree-selection-prompt");

interface InputOutput {
  files: string;
  outputName: string;
}
let inputAndOutput: InputOutput;

inquirer.registerPrompt("file-tree-selection", inquirerFileTreeSelection);

inquirer
  .prompt([
    {
      root: "./",
      type: "file-tree-selection",
      name: "files",
      message: "choose RxNorm .txt file"
    },
    {
      type: "input",
      name: "outputName",
      message: "Enter output file name. File will be generated on TXT extension"
    }
  ])
  .then((answers: InputOutput) => {
    console.log(answers.files);
    const workBook = XLSX.readFile(answers.files);

    const workSheet = workBook.Sheets[workBook.SheetNames[0]];

    const rxNormArray = XLSX.utils.sheet_to_json(
      workSheet
    ) as RxNormDrugOption[];

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
      }: "${current.optionLabelTemplate.templateText.replace(/'/g, "")}"}},`;
      return (prev += stringBuilder);
    }, "");

    writeFile(
      answers.outputName.concat('.txt'),
      JSON.stringify(
        parseToHOCON(`picklistOptions : [${picklistString}]`),
        null,
        2
      )
    );
  });
