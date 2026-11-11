/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

/**
 * Business logic for {@link Owner}s and their {@link Pet}s.
 */
@Service
class OwnerService {

	static final int PAGE_SIZE = 5;

	private final OwnerRepository owners;

	private final PetTypeRepository types;

	OwnerService(OwnerRepository owners, PetTypeRepository types) {
		this.owners = owners;
		this.types = types;
	}

	Owner findOwnerById(int ownerId) {
		return this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException(
					"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
	}

	Pet findPet(int ownerId, Integer petId) {
		if (petId == null) {
			return new Pet();
		}
		return findOwnerById(ownerId).getPet(petId);
	}

	Collection<PetType> findPetTypes() {
		return this.types.findPetTypes();
	}

	Owner saveOwner(Owner owner) {
		return this.owners.save(owner);
	}

	Owner updateOwner(int ownerId, Owner owner) {
		owner.setId(ownerId);
		return this.owners.save(owner);
	}

	String normalizeLastName(String lastName) {
		// empty string signifies broadest possible search
		return lastName == null ? "" : lastName.strip();
	}

	Page<Owner> findOwnersByLastName(String lastName, int page) {
		int validatedPage = Math.max(page, 1);
		Pageable pageable = PageRequest.of(validatedPage - 1, PAGE_SIZE);
		return this.owners.findByLastNameStartingWith(lastName, pageable);
	}

	boolean isPageOutOfBounds(int page, Page<?> results) {
		return page < 1 || page > Math.max(results.getTotalPages(), 1);
	}

	void validateNewPet(Owner owner, Pet pet, Errors errors) {
		if (StringUtils.hasText(pet.getName()) && pet.isNew() && owner.getPet(pet.getName(), true) != null) {
			errors.rejectValue("name", "duplicate", "already exists");
		}
		validateBirthDate(pet, errors);
	}

	void validateUpdatedPet(Owner owner, Pet pet, Errors errors) {
		String petName = pet.getName();
		if (StringUtils.hasText(petName)) {
			Pet existingPet = owner.getPet(petName, false);
			if (existingPet != null && !Objects.equals(existingPet.getId(), pet.getId())) {
				errors.rejectValue("name", "duplicate", "already exists");
			}
		}
		validateBirthDate(pet, errors);
	}

	private void validateBirthDate(Pet pet, Errors errors) {
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(LocalDate.now())) {
			errors.rejectValue("birthDate", "typeMismatch.birthDate");
		}
	}

	void addPet(Owner owner, Pet pet) {
		owner.addPet(pet);
		saveAndFlush(owner);
	}

	/**
	 * Updates the pet details if it exists or adds a new pet to the owner.
	 * @param owner The owner of the pet
	 * @param pet The pet with updated details
	 */
	void updatePet(Owner owner, Pet pet) {
		Integer id = pet.getId();
		Assert.state(id != null, "'pet.getId()' must not be null");
		Pet existingPet = owner.getPet(id);
		if (existingPet != null) {
			existingPet.setName(pet.getName());
			existingPet.setBirthDate(pet.getBirthDate());
			existingPet.setType(pet.getType());
		}
		else {
			owner.addPet(pet);
		}
		saveAndFlush(owner);
	}

	private void saveAndFlush(Owner owner) {
		try {
			this.owners.saveAndFlush(owner);
		}
		catch (DataIntegrityViolationException ex) {
			if (isDuplicatePetNameViolation(ex)) {
				throw new DuplicatePetNameException(ex);
			}
			throw ex;
		}
	}

	private boolean isDuplicatePetNameViolation(DataIntegrityViolationException ex) {
		String message = ex.getMessage();
		return message != null && message.toLowerCase().contains("unique_owner_pet_name");
	}

	/**
	 * Thrown when persisting a pet violates the unique owner/pet-name constraint.
	 */
	static class DuplicatePetNameException extends RuntimeException {

		DuplicatePetNameException(Throwable cause) {
			super(cause);
		}

	}

}
