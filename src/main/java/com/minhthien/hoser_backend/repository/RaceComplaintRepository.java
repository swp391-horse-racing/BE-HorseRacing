package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceComplaint;
import com.minhthien.hoser_backend.enums.RaceComplaintStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RaceComplaintRepository extends JpaRepository<RaceComplaint, Long> {
    @EntityGraph(attributePaths = {"race", "complainantOwner", "accusedOwner", "accusedParticipant",
            "accusedParticipant.horse"})
    List<RaceComplaint> findByComplainantOwnerIdOrAccusedOwnerIdOrderByCreatedAtDesc(Long complainantOwnerId,
                                                                                     Long accusedOwnerId);

    @EntityGraph(attributePaths = {"race", "complainantOwner", "accusedOwner", "accusedParticipant",
            "accusedParticipant.horse"})
    List<RaceComplaint> findByStatusOrderByCreatedAtDesc(RaceComplaintStatus status);

    @EntityGraph(attributePaths = {"race", "complainantOwner", "accusedOwner", "accusedParticipant",
            "accusedParticipant.horse"})
    List<RaceComplaint> findAllByOrderByCreatedAtDesc();
}
