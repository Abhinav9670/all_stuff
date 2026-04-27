const { formatPrice } = require('./utils');

exports.calcQuoteDonation = ({ quote, newDonation, isGetBag }) => {
    const { donationAmount = 0 } = quote

    let adjustedDonation = newDonation;
    if (newDonation === undefined)
        adjustedDonation = Number(donationAmount || 0)
    quote.donationAmount = adjustedDonation;

    return { quote, adjustedDonation }
}
