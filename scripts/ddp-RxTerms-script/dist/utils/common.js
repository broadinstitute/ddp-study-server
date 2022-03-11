"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.writeFile = exports.parseToHOCON = void 0;
exports.parseToHOCON = require("hocon-parser");
const fs = require("fs");
const writeFile = (fileName, value) => {
    return fs.writeFile(fileName, value, (err) => {
        if (err) {
            return console.log(err);
        }
        console.log("The file was saved!");
    });
};
exports.writeFile = writeFile;
