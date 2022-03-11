"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
const XLSX = __importStar(require("xlsx"));
const common_1 = require("./utils/common");
const fileName = "RxTerms202202.txt";
const workBook = XLSX.readFile(fileName);
const workSheet = workBook.Sheets[workBook.SheetNames[0]];
const rxNormArray = XLSX.utils.sheet_to_json(workSheet);
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
    const stringBuilder = `{${Object.keys(current)[0]}: ${current.stableId} optionLabelTemplate:{${Object.keys(current.optionLabelTemplate)[0]}: "${current.optionLabelTemplate.templateType}" ${Object.keys(current.optionLabelTemplate)[1]}: "${current.optionLabelTemplate.templateText.replace(/[^a-zA-Z0-9 ]/g, "")}"}},`;
    return (prev += stringBuilder);
}, "");
(0, common_1.writeFile)("output.txt", JSON.stringify((0, common_1.parseToHOCON)(`picklistOptions : [${picklistString}]`), null, 2));
