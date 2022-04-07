export const parseToHOCON = require("hocon-parser");

import * as fs from 'fs';

export const writeFile = (fileName: string, value: string) => {
  return fs.writeFile(fileName, value, err => {
    if (err) {
      return console.log(err);
    }
    console.log("The file was saved!");
  });
};
