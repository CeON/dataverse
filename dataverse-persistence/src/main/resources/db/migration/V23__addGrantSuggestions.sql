CREATE TABLE IF NOT EXISTS grantSuggestion (
    id SERIAL NOT NULL,
    grantagency varchar(255) NOT NULL,
    grantagencyacronym varchar(255) NOT NULL,
    fundingprogram varchar(255) NOT NULL,
    suggestionname varchar(255) NOT NULL,
    suggestionnamelocale varchar(255) NOT NULL
);