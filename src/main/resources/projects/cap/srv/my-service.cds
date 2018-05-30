using my.app from '../db/data-model';

// [ADDING EXTERNAL SERVICE] To add entities from external services:
// [ADDING EXTERNAL SERVICE] - STEP 1 - Add a data model from an external service to the project (by selecting the relevant menu option in SAP Web IDE).
// [ADDING EXTERNAL SERVICE] - STEP 2 - Add a reference to the external service model file:
// using <external_service_name> as <alias_name> from '../srv/external/csn/<external_service_name>';

service CatalogService {

	entity MyEntity {
	    key ID : Integer;
	}

	// [ADDING EXTERNAL SERVICE] - STEP 3 - Add a new entity to the exposed service model:
	// @cds.persistence.skip
	// entity <entity_name> as projection on <alias_name>.<external_entity_name>;

}
