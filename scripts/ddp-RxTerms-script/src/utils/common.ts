export const parseToHOCON = require("hocon-parser");
export const inquirer = require("inquirer");

const fs = require("fs");

export const writeFile = (fileName: string, value: string) => {
  return fs.writeFile(fileName, value, (err: string) => {
    if (err) {
      return console.log(err);
    }
    console.log("The file was saved!");
  });
};


const inquirerFileTreeSelection = require("inquirer-file-tree-selection-prompt");
inquirer.registerPrompt("file-tree-selection", inquirerFileTreeSelection);
