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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.samples.petclinic.owner.OwnerService.DuplicatePetNameException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link OwnerService}.
 */
@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

	@Mock
	private OwnerRepository owners;

	@Mock
	private PetTypeRepository types;

	private OwnerService service;

	private Owner owner;

	private Pet petty;

	private Pet doggy;

	@BeforeEach
	void setup() {
		this.service = new OwnerService(this.owners, this.types);
		this.owner = new Owner();
		this.owner.setId(1);
		this.petty = pet(1, "petty");
		this.doggy = pet(2, "doggy");
		this.owner.addPet(this.petty);
		this.owner.addPet(this.doggy);
	}

	private static Pet pet(Integer id, String name) {
		Pet pet = new Pet();
		pet.setId(id);
		pet.setName(name);
		pet.setBirthDate(LocalDate.of(2015, 2, 12));
		return pet;
	}

	private static Errors errorsFor(Pet pet) {
		return new BeanPropertyBindingResult(pet, "pet");
	}

	@Test
	void findOwnerByIdReturnsOwner() {
		given(this.owners.findById(1)).willReturn(Optional.of(this.owner));
		assertThat(this.service.findOwnerById(1)).isSameAs(this.owner);
	}

	@Test
	void findOwnerByIdThrowsWhenMissing() {
		given(this.owners.findById(99)).willReturn(Optional.empty());
		assertThatThrownBy(() -> this.service.findOwnerById(99)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("99");
	}

	@Test
	void findPetReturnsNewPetForNullId() {
		assertThat(this.service.findPet(1, null).isNew()).isTrue();
	}

	@Test
	void findPetReturnsOwnersPet() {
		given(this.owners.findById(1)).willReturn(Optional.of(this.owner));
		assertThat(this.service.findPet(1, 2)).isSameAs(this.doggy);
	}

	@Test
	void findPetTypesDelegatesToRepository() {
		PetType cat = new PetType();
		given(this.types.findPetTypes()).willReturn(List.of(cat));
		assertThat(this.service.findPetTypes()).containsExactly(cat);
	}

	@Test
	void normalizeLastName() {
		assertThat(this.service.normalizeLastName(null)).isEmpty();
		assertThat(this.service.normalizeLastName("   ")).isEmpty();
		assertThat(this.service.normalizeLastName(" Franklin ")).isEqualTo("Franklin");
	}

	@Test
	void findOwnersByLastNameClampsAndPaginates() {
		given(this.owners.findByLastNameStartingWith(eq("F"), any(Pageable.class))).willReturn(Page.empty());
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

		this.service.findOwnersByLastName("F", 0);
		this.service.findOwnersByLastName("F", 1);
		this.service.findOwnersByLastName("F", 3);

		verify(this.owners, times(3)).findByLastNameStartingWith(eq("F"), captor.capture());
		assertThat(captor.getAllValues()).containsExactly(PageRequest.of(0, OwnerService.PAGE_SIZE),
				PageRequest.of(0, OwnerService.PAGE_SIZE), PageRequest.of(2, OwnerService.PAGE_SIZE));
	}

	@Test
	void isPageOutOfBounds() {
		Page<Owner> onePage = new PageImpl<>(List.of(this.owner));
		assertThat(this.service.isPageOutOfBounds(0, onePage)).isTrue();
		assertThat(this.service.isPageOutOfBounds(2, onePage)).isTrue();
		assertThat(this.service.isPageOutOfBounds(1, onePage)).isFalse();
		assertThat(this.service.isPageOutOfBounds(1, Page.empty())).isFalse();
	}

	@Test
	void saveOwnerDelegatesToRepository() {
		this.service.saveOwner(this.owner);
		verify(this.owners).save(this.owner);
	}

	@Test
	void updateOwnerSetsIdAndSaves() {
		Owner form = new Owner();
		this.service.updateOwner(7, form);
		assertThat(form.getId()).isEqualTo(7);
		verify(this.owners).save(form);
	}

	@Test
	void validateNewPetRejectsDuplicateName() {
		Pet pet = pet(null, "PETTY");
		Errors errors = errorsFor(pet);
		this.service.validateNewPet(this.owner, pet, errors);
		assertThat(errors.getFieldError("name").getCode()).isEqualTo("duplicate");
	}

	@Test
	void validateNewPetRejectsFutureBirthDate() {
		Pet pet = pet(null, "Betty");
		pet.setBirthDate(LocalDate.now().plusDays(1));
		Errors errors = errorsFor(pet);
		this.service.validateNewPet(this.owner, pet, errors);
		assertThat(errors.getFieldError("birthDate").getCode()).isEqualTo("typeMismatch.birthDate");
	}

	@Test
	void validateNewPetAcceptsValidPet() {
		Pet pet = pet(null, "Betty");
		Errors errors = errorsFor(pet);
		this.service.validateNewPet(this.owner, pet, errors);
		assertThat(errors.hasErrors()).isFalse();
	}

	@Test
	void validateUpdatedPetAllowsSameNameOnSamePet() {
		Pet pet = pet(1, "petty");
		Errors errors = errorsFor(pet);
		this.service.validateUpdatedPet(this.owner, pet, errors);
		assertThat(errors.hasErrors()).isFalse();
	}

	@Test
	void validateUpdatedPetRejectsNameOfAnotherPet() {
		Pet pet = pet(2, "petty");
		Errors errors = errorsFor(pet);
		this.service.validateUpdatedPet(this.owner, pet, errors);
		assertThat(errors.getFieldError("name").getCode()).isEqualTo("duplicate");
	}

	@Test
	void addPetAddsAndFlushes() {
		Pet pet = pet(null, "Betty");
		this.service.addPet(this.owner, pet);
		assertThat(this.owner.getPets()).contains(pet);
		verify(this.owners).saveAndFlush(this.owner);
	}

	@Test
	void addPetTranslatesUniqueConstraintViolation() {
		given(this.owners.saveAndFlush(this.owner))
			.willThrow(new DataIntegrityViolationException("Duplicate key: UNIQUE_OWNER_PET_NAME"));
		assertThatThrownBy(() -> this.service.addPet(this.owner, pet(null, "Betty")))
			.isInstanceOf(DuplicatePetNameException.class);
	}

	@Test
	void addPetRethrowsOtherIntegrityViolations() {
		given(this.owners.saveAndFlush(this.owner)).willThrow(new DataIntegrityViolationException("other"));
		assertThatThrownBy(() -> this.service.addPet(this.owner, pet(null, "Betty")))
			.isExactlyInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void updatePetUpdatesExistingPetInPlace() {
		PetType hamster = new PetType();
		Pet form = pet(1, "Betty");
		form.setBirthDate(LocalDate.of(2020, 1, 1));
		form.setType(hamster);

		this.service.updatePet(this.owner, form);

		assertThat(this.petty.getName()).isEqualTo("Betty");
		assertThat(this.petty.getBirthDate()).isEqualTo(LocalDate.of(2020, 1, 1));
		assertThat(this.petty.getType()).isSameAs(hamster);
		assertThat(this.owner.getPets()).hasSize(2);
		verify(this.owners).saveAndFlush(this.owner);
	}

	@Test
	void updatePetAddsPetWhenNotFound() {
		Pet form = pet(42, "Newbie");
		this.service.updatePet(this.owner, form);
		assertThat(this.owner.getPets()).contains(form);
		verify(this.owners).saveAndFlush(this.owner);
	}

}
