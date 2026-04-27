exports.log = (a, b, c, d) => {
  if (process.env.NODE_ENV === 'development') {
    console.log.apply([], [a, b, c, d]);
  }
};

exports.logError = (a, b, c, d) => {
  try {
    console.error.apply([], JSON.stringify([a, b, c, d]));
  } catch (e) {
    console.log(e.message);
  }
};

exports.stringifyError = err => {
  return JSON.stringify(err, ['message', 'arguments', 'type', 'name', 'stack']);
};