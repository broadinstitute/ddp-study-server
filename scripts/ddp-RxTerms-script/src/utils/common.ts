export const parseToHOCON = require("hocon-parser");

const fs = require("fs");

export const writeFile = (fileName: string, value: string) => {
  return fs.writeFile(fileName, value, (err: string) => {
    if (err) {
      return console.log(err);
    }
    console.log("The file was saved!");
  });
};
