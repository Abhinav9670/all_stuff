const { logError } = require("./utils");

exports.processRules = ({ rulesData, data }) => {
    const ruleResponse = [];
    const ruleOutput = [];
  try {
    const { rtoCount = 0 } = data;
    function parse(str) {
      return Function(`'use strict'; return (${str})`)();
    }
    rulesData.forEach(rule => {
      const { criteria, input1, input2, operator, output, id } = rule;
      let ruleExpression = ``;
      switch (criteria) {
        case "returnOrderCount":
          if (operator === "between") {
            ruleExpression = `${rtoCount} >= ${input1} && ${rtoCount} <= ${input2}`;
          } else {
            ruleExpression = `${rtoCount} ${operator} ${input1}`;
          }
          break;
        default:
          break;
      }
      const evalExpression = parse(ruleExpression);
      if (evalExpression) {
        ruleResponse.push(id);
        ruleOutput.push(output);
      }
    });

  } catch (e) {
     logError(e,"error fraud rules processRules")
  }
  return { ruleResponse, ruleOutput };

};
