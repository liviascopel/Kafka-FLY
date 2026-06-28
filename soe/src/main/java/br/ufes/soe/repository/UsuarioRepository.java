package br.ufes.soe.repository;

import org.springframework.data.repository.CrudRepository;

import br.ufes.soe.domain.Usuario;
import jakarta.transaction.Transactional;

public interface UsuarioRepository extends CrudRepository<Usuario, Long>{
    @Transactional
    boolean existsByCpf(String cpf);
}
